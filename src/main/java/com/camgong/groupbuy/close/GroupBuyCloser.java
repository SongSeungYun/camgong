package com.camgong.groupbuy.close;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.camgong.groupbuy.GroupBuy;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyFailed;
import com.camgong.groupbuy.GroupBuyRepository;
import com.camgong.groupbuy.ParticipationRepository;

/**
 * 공구 한 건의 마감 확정. 서비스와 다른 클래스의 빈이어야 프록시를 거쳐 건마다 새 트랜잭션이 시작된다
 * (같은 클래스의 메서드 호출은 프록시를 우회해 @Transactional 이 무시된다).
 */
@Component
public class GroupBuyCloser {

    public enum Outcome {
        CLOSED, SKIPPED
    }

    private final GroupBuyRepository groupBuys;
    private final ParticipationRepository participations;
    private final ApplicationEventPublisher events;

    GroupBuyCloser(GroupBuyRepository groupBuys, ParticipationRepository participations, ApplicationEventPublisher events) {
        this.groupBuys = groupBuys;
        this.participations = participations;
        this.events = events;
    }

    /**
     * id 하나를 SKIP LOCKED 로 선점한다(research 결정 1). 못 잡으면 다른 곳이 잡았거나 이미 확정됐거나 조건이 안 맞는 것이라
     * 건너뛴다. 잡았으면 조건(OPEN, 마감 ≤ cutoff, 확보 < 총 수량)이 락 안에서 다시 확인된 상태라 같은 트랜잭션에서
     * FAILED 로 바꾸고 이벤트를 낸다 — 확정과 이벤트가 한 경계 안이라 한 공구에 한 번만 나간다(롤백되면 이벤트도 없다).
     */
    @Transactional
    public Outcome closeOne(Long id, Instant cutoff, Instant now) {
        GroupBuy g = groupBuys.claimForClose(id, cutoff).orElse(null);
        if (g == null) {
            return Outcome.SKIPPED;
        }
        g.fail(now);
        // 선점 뒤에 읽으므로 잠금 읽기로 읽는다: 일반 SELECT 는 REPEATABLE READ 에서 선점 대기 전 스냅샷을 볼 수 있다.
        // 개설자는 반드시 포함한다(계약): 개설자 참여 행이 없는 데이터가 있어도 알림 대상에서 빠지지 않게 명시적으로 합친다.
        Set<Long> recipientSet = new LinkedHashSet<>();
        recipientSet.add(g.getOrganizerId());
        participations.findLockedByGroupBuyId(id).forEach(p -> recipientSet.add(p.getMemberId()));
        List<Long> recipients = List.copyOf(recipientSet);
        events.publishEvent(new GroupBuyFailed(UUID.randomUUID().toString(), id, g.getTitle(), recipients));
        return Outcome.CLOSED;
    }
}
