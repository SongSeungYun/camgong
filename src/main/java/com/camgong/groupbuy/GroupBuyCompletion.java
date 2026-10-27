package com.camgong.groupbuy;

import java.util.List;

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

    GroupBuyCompletion(ApplicationEventPublisher events, ParticipationRepository participations) {
        this.events = events;
        this.participations = participations;
    }

    /** 수신자 목록을 이미 읽어 둔 호출자용(001 수정). */
    public void publish(Long groupBuyId, List<Long> recipientIds) {
        events.publishEvent(new GroupBuyCompleted(groupBuyId, recipientIds));
    }

    /** 락 안에서 참여 행을 읽어 수신자를 모은다(004). 같은 트랜잭션에서 방금 쓴 행까지 보이도록 잠금 읽기다. */
    public void publish(Long groupBuyId) {
        publish(groupBuyId, participations.findLockedByGroupBuyId(groupBuyId).stream().map(Participation::getMemberId).toList());
    }
}
