package com.camgong.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.camgong.common.ApiException;
import com.camgong.member.Member;
import com.camgong.member.MemberRepository;

/**
 * 회원 탈퇴(010). 회원 행은 남기고 알아볼 수 있는 값만 지운다(plan 결정 1).
 * 비밀번호 확인과 실패 횟수는 트랜잭션 밖에서 한다: 횟수는 한 문장으로 바로 커밋되어야 뒤의 예외 롤백에 휩쓸리지 않는다(결정 3·4-2).
 */
@Service
public class WithdrawService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WithdrawService.class);
    private static final int BCRYPT_MAX_BYTES = 72;
    /** 어떤 비밀번호와도 맞지 않는 값(BCrypt 형식이 아니다). */
    private static final String NO_PASSWORD = "withdrawn";

    /** 409 ACTIVE_GROUPBUY 의 blockers 한 줄. status 는 003 표시 상태(OPEN | PENDING_CLOSE). */
    public record Blocker(long groupBuyId, String title, String role, String status) {
    }

    private final MemberRepository members;
    private final PasswordEncoder encoder;
    private final LoginService login;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    public WithdrawService(MemberRepository members, PasswordEncoder encoder, LoginService login, JdbcClient jdbc,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.members = members;
        this.encoder = encoder;
        this.login = login;
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public void withdraw(long memberId, String password) {
        // 공백만이면 빈 값이다(가입이 공백 비밀번호를 막아 맞을 수 없다). 실패 횟수에 넣지 않는다.
        if (password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.",
                    List.of(new ApiException.Field("password", "비밀번호를 입력해 주세요.")));
        }
        Member member = members.findById(memberId).filter(m -> !m.isWithdrawn()).orElseThrow(WithdrawService::unauthenticated);
        String emailKey = member.getEmailKey();
        Instant now = Instant.now(clock);

        // 로그인과 같은 잠금(002): 시도를 먼저 세고(바로 커밋), 잠겨 있으면 BCrypt 없이 429.
        if (!login.countAttempt(emailKey, now)) {
            throw login.locked(emailKey, now);
        }
        boolean comparable = password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
        if (!comparable || !encoder.matches(password, member.getPasswordHash())) {
            // 401 을 쓰지 않는다: 프론트는 401 이면 로그인 화면으로 보낸다.
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호가 맞지 않습니다.",
                    List.of(new ApiException.Field("password", "비밀번호가 맞지 않습니다.")));
        }
        login.completeSuccess(emailKey);

        // 탈퇴의 notification 삭제(recipient 인덱스 → PK)와 NotificationCleanup(created_at 인덱스 → PK)은 잠금 순서가 반대라 드물게
        // 교착한다. 한 번만 다시 한다(새 트랜잭션이라 잠금도 처음부터 다시 잡는다).
        try {
            tx.executeWithoutResult(s -> anonymise(memberId, emailKey, now));
        } catch (PessimisticLockingFailureException e) {     // 교착(1213)·잠금 대기 초과(1205) 둘 다
            tx.executeWithoutResult(s -> anonymise(memberId, emailKey, now));
        }
        // 백업에서 복구하면 백업 뒤의 탈퇴가 되살아난다. 복구 절차가 이 줄로 다시 익명화한다(결정 5). 이메일·이름은 남기지 않는다.
        log.info("withdrawn memberId={}", memberId);
        // 세션은 커밋 뒤에 지운다. 트랜잭션 안(회원 행 X 잠금을 쥔 채)에서 지우면 같은 회원의 세션 행을 넣는 문장과 교착할 수 있다:
        // INSERT … ON DUPLICATE KEY UPDATE(테스트 도우미 as())로는 재현됐고, 실제 로그인의 단순 INSERT 로는 900회 경쟁에서 재현되지 않았다.
        // 지우기 전에 남은 세션이나 그 사이 들어온 세션은 AuthFilter 가 탈퇴한 회원이라 받지 않고 지운다. 이 삭제가 실패해도 탈퇴는 이미 커밋됐으므로 204 로 끝내고 경고만 남긴다. 남은 행은 SessionCleanup 이 14일 TTL 로 지운다.
        try {
            deleteSessions(memberId);
        } catch (RuntimeException e) {
            log.warn("withdraw-session-cleanup-failed memberId={} reason={}", memberId, e.getClass().getSimpleName());
        }
    }

    /** 커밋 뒤 세션 삭제. 실패해도 탈퇴가 204 로 끝나는지 테스트가 가로채는 지점이다. */
    void deleteSessions(long memberId) {
        jdbc.sql("DELETE FROM login_session WHERE member_id = :id").param("id", memberId).update();
    }

    /**
     * 첫 문장이 회원 행 FOR UPDATE 다. 그 뒤의 일반 SELECT 는 잠금을 받은 뒤에 스냅샷을 잡으므로, 잠금을 기다리는 동안 커밋된 새 참여·개설
     * (그쪽은 같은 행을 FOR SHARE 로 잡는다)이 보인다. 막는 조건은 잠금 읽기로 하지 않는다(FOR SHARE 면 006 체크표 읽기와 교착할 수 있다).
     */
    void anonymise(long memberId, String emailKey, Instant now) {
        boolean active = jdbc.sql("SELECT withdrawn_at IS NULL FROM member WHERE id = :id FOR UPDATE")
                .param("id", memberId).query(Boolean.class).optional().orElse(false);
        if (!active) {
            throw unauthenticated();                        // 같은 회원의 탈퇴가 동시에 와서 먼저 끝났다
        }
        List<Blocker> blockers = blockers(memberId, now);
        if (!blockers.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_GROUPBUY", "진행 중인 공구가 있어 탈퇴할 수 없습니다.")
                    .property("blockers", blockers);
        }
        jdbc.sql("""
                UPDATE member SET name = :name, display_name_key = NULL, email = NULL, email_key = NULL,
                    password_hash = :noPassword, withdrawn_at = :now
                WHERE id = :id""")
                .param("name", Member.WITHDRAWN_NAME)
                .param("noPassword", NO_PASSWORD)
                .param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("id", memberId)
                .update();
        clearOwnContent(memberId);
        jdbc.sql("DELETE FROM notification WHERE recipient_id = :id").param("id", memberId).update();
        jdbc.sql("DELETE FROM pending_signup WHERE email_key = :key").param("key", emailKey).update();
        jdbc.sql("DELETE FROM login_attempt WHERE email_key = :key").param("key", emailKey).update();
    }

    /**
     * 이 회원이 연 모든 공구의 설명과 create_guard 를 지운다(결정 5). 설명은 자유 텍스트라 연락처·계좌가 들어갈 수 있고, 끝난 공구는 본인도
     * 고칠 수 없다. 익명화 트랜잭션 안에서 부른다(실패하면 탈퇴 전체가 되돌아가는지 테스트가 가로채는 지점이다).
     * 여기까지 왔으면 이 회원이 연 공구는 모두 종료 상태라 바꾸는 경로가 없다. 수정·취소·참여·마감·체크가 그 행을 잠글 수는 있지만,
     * 잠근 뒤 회원 행을 기다리지 않으므로 잠깐 기다릴 뿐 교착하지 않는다. 잠금 순서는 member → group_buy 다.
     */
    void clearOwnContent(long memberId) {
        jdbc.sql("UPDATE group_buy SET description = NULL WHERE organizer_id = :id").param("id", memberId).update();
        jdbc.sql("DELETE FROM create_guard WHERE member_id = :id").param("id", memberId).update();
    }

    /** 모집 중(마감 확정 대기 포함) 공구 가운데 이 회원이 열었거나 참여한 것, 공구 id 오름차순. */
    private List<Blocker> blockers(long memberId, Instant now) {
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        return jdbc.sql("""
                SELECT id, title, organizer_id, deadline FROM group_buy WHERE organizer_id = :id AND status = 'OPEN'
                UNION
                SELECT g.id, g.title, g.organizer_id, g.deadline FROM participation p JOIN group_buy g ON g.id = p.group_buy_id
                WHERE p.member_id = :id AND g.status = 'OPEN'
                ORDER BY id""")
                .param("id", memberId)
                .query((rs, i) -> new Blocker(rs.getLong("id"), rs.getString("title"),
                        rs.getLong("organizer_id") == memberId ? "ORGANIZER" : "PARTICIPANT",
                        rs.getObject("deadline", LocalDateTime.class).toInstant(ZoneOffset.UTC).isAfter(at)
                                ? "OPEN" : "PENDING_CLOSE"))
                .list();
    }

    private static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.");
    }
}
