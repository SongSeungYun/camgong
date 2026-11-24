package com.camgong.retention;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 끝난 공구 1년 보관(012). 하루 한 번, 끝난 지 1년 지난 공구를 참여·알림과 함께 100개씩 지우고(공구 하나는 통째로 지워지거나 남는다),
 * 그 뒤 함께한 공구가 없는 탈퇴 회원 행을 100개씩 지운다. 테스트는 run() 을 직접 부른다.
 */
// ponytail: 단일 인스턴스 전제(정리 두 개가 같은 묶음을 잡으면 하나는 잠금을 기다렸다가 빈 묶음을 지운다)
@Component
public class RetentionCleanup {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanup.class);
    static final int BATCH = 100;
    /** 1년보다 짧으면 기동하지 않는다(단위 없는 "365" 는 365ms 다. 다음 정리가 끝난 공구를 모두 지우고, 되돌릴 길은 7일 백업뿐이다). */
    static final Duration MIN_ENDED_FOR = Duration.ofDays(365);
    /**
     * 참여·알림은 group_buy_id 인덱스 범위로만 지운다(테스트가 실행 계획을 본다). 묶음이 작은 표의 큰 몫이면 옵티마이저가 전체 훑기를 골라
     * RR 에서 모든 행을 잠근다(모집 중 공구의 취소·수정과 교착). 옵티마이저 힌트로 고정한다: 단일 표 DELETE 는 FORCE INDEX 를 받지 않고,
     * 다중 표 문법의 FORCE INDEX 도 작은 표에서는 전체 훑기를 그대로 골랐다(테스트로 확인).
     */
    static final String DELETE_NOTIFICATIONS =
            "DELETE /*+ INDEX(notification idx_notification_group_buy) */ FROM notification WHERE group_buy_id IN (:ids)";
    static final String DELETE_PARTICIPATIONS =
            "DELETE /*+ INDEX(participation uq_participation_group_member) */ FROM participation WHERE group_buy_id IN (:ids)";
    /** 고른 공구를 PK 로만 잠근다(테스트가 실행 계획을 본다). */
    static final String LOCK_GROUP_BUYS =
            "SELECT id FROM group_buy FORCE INDEX (PRIMARY) WHERE id IN (:ids) AND ended_at < :cut ORDER BY id FOR UPDATE";

    public record Result(int groupBuys, int members) {}

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration endedFor;
    private final TransactionTemplate tx;

    RetentionCleanup(JdbcClient jdbc, Clock clock, PlatformTransactionManager transactionManager,
            @Value("${camgong.retention.ended-for:365d}") Duration endedFor) {
        if (endedFor == null || endedFor.compareTo(MIN_ENDED_FOR) < 0) {
            throw new IllegalArgumentException("camgong.retention.ended-for 는 365일(365d) 이상이어야 한다: " + endedFor);
        }
        this.jdbc = jdbc;
        this.clock = clock;
        this.endedFor = endedFor;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Result run() {
        LocalDateTime cut = LocalDateTime.ofInstant(Instant.now(clock).minus(endedFor), ZoneOffset.UTC);
        int groupBuys = drain(() -> deleteGroupBuys(cut));
        int members = drain(this::deleteMembers);
        // 012 결정 6: 10초 중복 개설 막기용 공구 글 해시. 활동 회원 것도 1일 지나면 쓸모가 없다(공구를 지운 뒤에도 남지 않게).
        // ponytail: create_guard 에 created_at 인덱스가 없어 표를 훑는다. 행이 적고 하루 한 번이라 수용, 커지면 인덱스
        retryOnce(() -> jdbc.sql("DELETE FROM create_guard WHERE created_at < :cut")
                .param("cut", LocalDateTime.ofInstant(Instant.now(clock).minus(Duration.ofDays(1)), ZoneOffset.UTC)).update());
        log.info("retention-cleanup groupBuys={} members={}", groupBuys, members);
        return new Result(groupBuys, members);
    }

    /** 묶음을 0 이 나올 때까지 되풀이한다. */
    private static int drain(IntSupplier batch) {
        int total = 0;
        int n;
        do {
            n = retryOnce(batch);
            total += n;
        } while (n > 0);
        return total;
    }

    /** 교착(1213)·잠금 대기 초과면 한 번 다시 한다(트랜잭션이 통째로 되돌아갔다). */
    private static int retryOnce(IntSupplier work) {
        try {
            return work.getAsInt();
        } catch (PessimisticLockingFailureException e) {
            log.info("retention-cleanup-retry reason={}", e.getClass().getSimpleName());
            return work.getAsInt();
        }
    }

    /**
     * 후보는 잠그지 않고 고른 뒤 공구 행을 PK 로 잠근다: ended_at 인덱스 범위를 잠그면 그 끝의 간격까지 잠겨 지금 끝나는 공구(ended_at 기록)가
     * 정리를 기다린다(인덱스를 PRIMARY 로 고정한다: 밀린 공구가 많으면 옵티마이저가 ended_at 인덱스를 고를 수 있다). 공구 → 참여 순서도 006 체크 토글(공구 읽기 → 참여 갱신)과 같다. 끝난 공구는 상태·ended_at 이 다시 바뀌지 않는다.
     */
    private int deleteGroupBuys(LocalDateTime cut) {
        List<Long> candidates = jdbc.sql("SELECT id FROM group_buy WHERE ended_at < :cut ORDER BY id LIMIT " + BATCH)
                .param("cut", cut).query(Long.class).list();
        if (candidates.isEmpty()) {
            return 0;
        }
        return tx.execute(s -> {
            // 잠금 순서는 다른 경로와 같은 회원 → 공구다(plan 결정 2). 공구 삭제는 개설자 보조 인덱스 항목도 잠그는데, 개설자 탈퇴의 설명 지우기는
            // 회원 X → 그 항목 → 공구 PK 순서라, 회원 행을 먼저 잡지 않으면 서로 반대로 잡아 교착한다.
            List<Long> organizers = jdbc.sql("SELECT DISTINCT organizer_id FROM group_buy WHERE id IN (:ids) ORDER BY organizer_id")
                    .param("ids", candidates).query(Long.class).list();
            jdbc.sql("SELECT id FROM member WHERE id IN (:ids) ORDER BY id FOR SHARE").param("ids", organizers)
                    .query(Long.class).list();
            List<Long> ids = jdbc.sql(LOCK_GROUP_BUYS)
                    .param("ids", candidates).param("cut", cut).query(Long.class).list();
            if (ids.isEmpty()) {
                return 0;
            }
            jdbc.sql(DELETE_NOTIFICATIONS).param("ids", ids).update();
            jdbc.sql(DELETE_PARTICIPATIONS).param("ids", ids).update();
            return jdbc.sql("DELETE FROM group_buy WHERE id IN (:ids)").param("ids", ids).update();
        });
    }

    /**
     * 함께한 공구(연 공구·참여)가 하나도 남지 않은 탈퇴 회원(plan 결정 3). 탈퇴한 회원은 공구를 열거나 참여할 수 없어 고른 뒤 조건이 바뀌지 않는다.
     * 탈퇴 때 지웠어야 할 행이 남아 있어도 지운다(FK 가 있는 세션·재설정 기록은 먼저).
     */
    // ponytail: 후보 고르기가 member 를 훑는다(withdrawn_at 인덱스 없음). 하루 한 번이라 수용, 회원이 많아지면 인덱스
    private int deleteMembers() {
        List<Long> ids = jdbc.sql("""
                SELECT m.id FROM member m WHERE m.withdrawn_at IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM group_buy g WHERE g.organizer_id = m.id)
                  AND NOT EXISTS (SELECT 1 FROM participation p WHERE p.member_id = m.id)
                ORDER BY m.id LIMIT :batch""")
                .param("batch", BATCH).query(Long.class).list();
        if (ids.isEmpty()) {
            return 0;
        }
        return tx.execute(s -> {
            jdbc.sql("DELETE FROM login_session WHERE member_id IN (:ids)").param("ids", ids).update();
            jdbc.sql("DELETE FROM password_reset WHERE member_id IN (:ids)").param("ids", ids).update();
            jdbc.sql("DELETE FROM create_guard WHERE member_id IN (:ids)").param("ids", ids).update();
            jdbc.sql("DELETE FROM notification WHERE recipient_id IN (:ids)").param("ids", ids).update();
            return jdbc.sql("DELETE FROM member WHERE id IN (:ids) AND withdrawn_at IS NOT NULL").param("ids", ids).update();
        });
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "camgong.retention.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {

        private final RetentionCleanup cleanup;

        Scheduling(RetentionCleanup cleanup) {
            this.cleanup = cleanup;
        }

        @Scheduled(fixedDelayString = "${camgong.retention.cleanup.interval:1d}", initialDelayString = "PT5M")
        void scheduled() {
            try {
                cleanup.run();
            } catch (RuntimeException e) {
                log.warn("retention-cleanup-failed reason={}", e.getClass().getSimpleName());
            }
        }
    }
}
