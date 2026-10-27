package com.camgong.groupbuy;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;

/**
 * 모집 완료 이벤트 발행을 한곳에 모은다. 001 수정과 004 참여·수량 변경이 같은 방식으로 낸다:
 * 개설자를 포함한 모든 참여 행의 회원에게, 호출자의 트랜잭션이 커밋된 뒤에만 전달된다.
 * 완료 판정은 {@link GroupBuy#completeIfFull()} 한 곳에서 한다.
 */
@Component
public class GroupBuyCompletion {

    private final ApplicationEventPublisher events;
    private final ParticipationRepository participations;
    private final GroupBuyRepository groupBuys;

    GroupBuyCompletion(ApplicationEventPublisher events, ParticipationRepository participations, GroupBuyRepository groupBuys) {
        this.events = events;
        this.participations = participations;
        this.groupBuys = groupBuys;
    }

    /** 수신자 목록과 제목을 이미 읽어 둔 호출자용(001 수정). eventId 는 발행 때 한 번 만든다. */
    public void publish(Long groupBuyId, String groupBuyTitle, List<Long> recipientIds) {
        events.publishEvent(new GroupBuyCompleted(UUID.randomUUID().toString(), groupBuyId, groupBuyTitle, recipientIds));
    }

    /** 락 안에서 참여 행을 읽어 수신자를 모은다(004). 같은 트랜잭션에서 방금 쓴 행까지 보이도록 잠금 읽기다. */
    public void publish(Long groupBuyId) {
        // 제목은 엔티티가 아니라 값으로 읽는다: 호출 쪽 게이트에 따라 영속성 컨텍스트에 낡은 엔티티가 있을 수 있다(제목은 모집 완료 직전까지만 바뀐다).
        String title = groupBuys.findTitle(groupBuyId);
        publish(groupBuyId, title, participations.findLockedByGroupBuyId(groupBuyId).stream().map(Participation::getMemberId).toList());
    }
}
