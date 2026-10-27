package com.camgong.groupbuy.join;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.camgong.common.ApiException;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;
import com.camgong.groupbuy.GroupBuyStatus;
import com.camgong.groupbuy.Participation;
import com.camgong.groupbuy.ParticipationRepository;

/**
 * 참여·수량 변경·참여 취소. 한 요청이 한 트랜잭션: 게이트가 group_buy 를 갱신하고, 참여 행을 바꾸고, 모집 완료면 이벤트를 낸다.
 * 하나라도 실패하면 모두 롤백된다. 락 순서는 항상 공구 행(게이트) → 참여 행이라 개설자 수정(001)과 교착하지 않는다.
 */
@Service
public class ParticipationService {

    private static final int MAX_ATTEMPTS = 3;

    /** created 가 true 면 새 참여(201), false 면 변화 없는 재시도·변경(200). */
    public record Joined(ParticipationResponse body, boolean created) {
    }

    private final JoinQueryRepository query;
    private final ParticipationRepository participations;
    private final QuantityGate gate;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ParticipationService(JoinQueryRepository query, ParticipationRepository participations, QuantityGate gate,
            ApplicationEventPublisher events, Clock clock, PlatformTransactionManager transactionManager) {
        this.query = query;
        this.participations = participations;
        this.gate = gate;
        this.events = events;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * 참여. 순서: 공구 없음 404 → 개설자 403 → 이미 참여(FAILED 409 / 같은 수량 200 / 다른 수량 409) → 게이트 → 참여 행 삽입 → 완료 이벤트.
     * 같은 회원의 동시 이중 요청은 UNIQUE 위반으로 드러나고, 롤백(예약도 함께 되돌아감) 뒤 다시 처리하면 "이미 참여"로 들어간다.
     */
    public Joined join(Long memberId, Long groupBuyId, ParticipationRequest r) {
        return retrying(() -> {
            Instant now = Instant.now(clock);
            GroupBuyHead head = requireJoinable(groupBuyId, memberId);
            Integer existing = query.findQuantity(groupBuyId, memberId).orElse(null);
            if (existing != null) {
                if (head.status() == GroupBuyStatus.FAILED) {
                    throw notJoinable("FAILED");
                }
                if (existing.equals(r.quantity())) {
                    return new Joined(response(groupBuyId, existing, head), false);
                }
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_JOINED", "이미 참여한 공구입니다. 수량 변경을 사용해 주세요.");
            }

            GateResult res = reserve(groupBuyId, memberId, null, r.quantity(), r.expectedUnitPrice(), now);
            try {
                participations.saveAndFlush(new Participation(groupBuyId, memberId, r.quantity(), now));
            } catch (DataIntegrityViolationException e) {
                // 같은 회원의 동시 이중 요청이 만드는 UNIQUE 위반만 재시도 대상이다. 다른 무결성 오류는 그대로 올린다.
                if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_participation_group_member")) {
                    throw e;
                }
                throw new RetryableConflict("같은 회원의 동시 참여", e);
            }
            publishIfCompleted(groupBuyId, res);
            return new Joined(response(groupBuyId, r.quantity(), res), true);
        });
    }

    /** 수량 변경. delta = 새 수량 − 기존 수량이고 delta 가 0 이어도 상태·마감·가격 확인을 위해 게이트를 지난다. */
    public ParticipationResponse change(Long memberId, Long groupBuyId, ParticipationRequest r) {
        return retrying(() -> {
            Instant now = Instant.now(clock);
            requireJoinable(groupBuyId, memberId);
            int old = query.findQuantity(groupBuyId, memberId).orElseThrow(ParticipationService::participationNotFound);

            GateResult res = reserve(groupBuyId, memberId, old, r.quantity() - old, r.expectedUnitPrice(), now);
            Participation mine = lockedMine(groupBuyId, memberId, old);
            mine.changeQuantity(r.quantity());
            participations.flush();
            publishIfCompleted(groupBuyId, res);
            return response(groupBuyId, r.quantity(), res);
        });
    }

    /** 참여 취소. 가격은 보지 않는다(expectedUnitPrice = null). */
    public void leave(Long memberId, Long groupBuyId) {
        retrying(() -> {
            Instant now = Instant.now(clock);
            requireJoinable(groupBuyId, memberId);
            int old = query.findQuantity(groupBuyId, memberId).orElseThrow(ParticipationService::participationNotFound);

            reserve(groupBuyId, memberId, old, -old, null, now);
            Participation mine = lockedMine(groupBuyId, memberId, old);
            participations.delete(mine);
            participations.flush();
            return null;
        });
    }

    /**
     * 게이트(공구 행 락) 뒤에 잠금 읽기로 내 참여 행을 다시 읽는다. 사전 검사 때의 수량과 다르거나 사라졌으면 그 사이에
     * 같은 회원의 다른 요청이 끝난 것이라 delta 가 틀렸다 → 롤백하고 처음부터 다시 한다(I1 보호).
     */
    private Participation lockedMine(Long groupBuyId, Long memberId, int expectedQuantity) {
        return participations.findLockedByGroupBuyIdAndMemberId(groupBuyId, memberId)
                .filter(p -> p.getQuantity() == expectedQuantity)
                .orElseThrow(() -> new RetryableConflict("읽은 뒤 참여 행이 바뀌었다", null));
    }

    /** 공구가 없거나 취소되면 404, 개설자면 403. 락 없는 사전 검사이며 최종 판정은 게이트가 한다. */
    private GroupBuyHead requireJoinable(Long groupBuyId, Long memberId) {
        GroupBuyHead head = query.findHead(groupBuyId).filter(h -> h.status() != GroupBuyStatus.CANCELLED)
                .orElseThrow(ParticipationService::groupBuyNotFound);
        if (head.organizerId().equals(memberId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORGANIZER_NOT_ALLOWED",
                    "개설자는 직접 참여할 수 없어요. 수량은 공구 수정에서 바꿔요.");
        }
        return head;
    }

    /**
     * 게이트를 지난다. 거부되면 판정에 쓴 delta 가 사전 검사(락 전 스냅샷)에 기댄 값이라, 그 사이 같은 회원의 다른 요청이 끝나
     * 내 참여 행이 바뀌었는지 잠금 읽기로 다시 본다. 바뀌었으면 거부를 확정하지 않고 새 트랜잭션에서 처음부터 다시 한다
     * (예: 같은 요청을 두 번 동시에 보냈을 때 뒤 요청은 거부가 아니라 "같은 수량이면 200"이어야 한다).
     *
     * @param seenQuantity 사전 검사에서 본 내 현재 수량. 참여 전이면 null
     */
    private GateResult reserve(Long groupBuyId, Long memberId, Integer seenQuantity, int delta, Integer expectedUnitPrice,
            Instant now) {
        GateResult res = gate.reserve(groupBuyId, delta, expectedUnitPrice, now);
        if (!res.isReserved()) {
            Integer actual = participations.findLockedByGroupBuyIdAndMemberId(groupBuyId, memberId)
                    .map(Participation::getQuantity).orElse(null);
            if (!java.util.Objects.equals(actual, seenQuantity)) {
                throw new RetryableConflict("거부를 판정하는 사이 내 참여가 바뀌었다", null);
            }
        }
        return switch (res.outcome()) {
            case RESERVED -> res;
            case NOT_FOUND -> throw groupBuyNotFound();
            case NOT_JOINABLE -> throw notJoinable(res.reason());
            case PRICE_CHANGED -> throw new ApiException(HttpStatus.CONFLICT, "PRICE_CHANGED",
                    "가격이 바뀌었습니다. 확인 후 다시 시도해 주세요.").property("currentUnitPrice", res.unitPrice());
            case INSUFFICIENT_QUANTITY -> throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_QUANTITY",
                    "남은 수량이 부족합니다.").property("remainingQuantity", res.totalQuantity() - res.securedQuantity());
            case BUSY -> throw busy();
        };
    }

    private void publishIfCompleted(Long groupBuyId, GateResult res) {
        if (res.completed()) {
            List<Long> everyone = participations.findLockedByGroupBuyId(groupBuyId).stream()
                    .map(Participation::getMemberId).toList();
            events.publishEvent(new GroupBuyCompleted(groupBuyId, everyone));
        }
    }

    private static ParticipationResponse response(Long groupBuyId, int quantity, GateResult res) {
        return ParticipationResponse.of(groupBuyId, quantity, res.unitPrice(), res.securedQuantity(), res.totalQuantity(),
                res.completed() ? GroupBuyStatus.COMPLETED : GroupBuyStatus.OPEN);
    }

    private static ParticipationResponse response(Long groupBuyId, int quantity, GroupBuyHead head) {
        return ParticipationResponse.of(groupBuyId, quantity, head.unitPrice(), head.securedQuantity(),
                head.totalQuantity(), head.status());
    }

    /**
     * 새 트랜잭션에서 다시 처리하면 풀리는 충돌(낙관적 버전 충돌, 교착, 동시 이중 요청)을 최대 3번까지 다시 시도한다.
     * 재시도는 반드시 새 트랜잭션이어야 한다: REPEATABLE READ 의 스냅샷은 트랜잭션 안에서 바뀌지 않는다.
     */
    private <T> T retrying(Supplier<T> work) {
        for (int attempt = 1;; attempt++) {
            try {
                return tx.execute(status -> work.get());
            } catch (RetryableConflict | ConcurrencyFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw busy();
                }
                sleepBriefly();
            }
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 25));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static ApiException groupBuyNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Not Found", "공구를 찾을 수 없습니다.");
    }

    private static ApiException participationNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PARTICIPATION_NOT_FOUND", "이 공구에 참여한 기록이 없습니다.");
    }

    private static ApiException notJoinable(String reason) {
        return ApiException.withReason(HttpStatus.CONFLICT, "NOT_JOINABLE", "참여할 수 없는 공구입니다.", reason);
    }

    private static ApiException busy() {
        return new ApiException(HttpStatus.CONFLICT, "BUSY", "요청이 몰려 처리하지 못했습니다. 잠시 뒤에 다시 시도해 주세요.");
    }
}
