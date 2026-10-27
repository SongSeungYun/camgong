package com.camgong.groupbuy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.camgong.common.ApiException;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCancelled;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyChanged;

@Service
public class GroupBuyService {

    private static final long MAX_DEADLINE_DAYS = 30;

    private final GroupBuyRepository groupBuys;
    private final ParticipationRepository participations;
    private final CreateGuard createGuard;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final GroupBuyCompletion completion;

    public GroupBuyService(GroupBuyRepository groupBuys,
            ParticipationRepository participations, CreateGuard createGuard, Clock clock,
            ApplicationEventPublisher events, GroupBuyCompletion completion) {
        this.completion = completion;
        this.groupBuys = groupBuys;
        this.participations = participations;
        this.createGuard = createGuard;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public GroupBuyResponse create(Long memberId, GroupBuyRequest r) {
        Instant now = Instant.now(clock);

        // 검증은 가드보다 먼저: 실패한 요청이 가드를 소모하면 같은 본문 재전송이 400 이 아닌 409 가 된다.
        validateForCreate(r, now);

        // 중복이면 409. 이 뒤에서 예외로 롤백되면 가드 행도 같이 롤백된다.
        if (!createGuard.pass(memberId, CreateGuard.fingerprint(r), now)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_REQUEST", "같은 내용의 요청이 이미 처리되었습니다.");
        }

        GroupBuy groupBuy = groupBuys.save(new GroupBuy(memberId, r.title(), r.description(), r.itemName(),
                r.unitPrice(), r.totalQuantity(), r.organizerQuantity(), r.deadline().toInstant(), r.category(),
                r.itemUrl(), r.openChatUrl(), now));
        participations.save(new Participation(groupBuy.getId(), memberId, r.organizerQuantity(), now));

        // 개설자 본인에게 돌려주므로 오픈채팅 링크는 항상 값을 준다.
        return GroupBuyResponse.from(groupBuy, r.organizerQuantity(), groupBuy.getOpenChatUrl());
    }

    /**
     * 락을 잡은 뒤 최신 값으로 검증한다. 순서는 data-model.md "수정 시 검증 순서":
     * 존재·취소(404) → 개설자(403) → OPEN·마감 전(409) → 마감 → 수량 → 반영 후 모집 완료 판정.
     */
    @Transactional
    public GroupBuyResponse update(Long memberId, Long id, GroupBuyRequest r) {
        Instant now = Instant.now(clock);
        GroupBuy g = lockModifiable(memberId, id, now);

        Participation organizerRow = participations.findLockedByGroupBuyIdAndMemberId(id, g.getOrganizerId())
                .orElseThrow(() -> new IllegalStateException("개설자 참여 행이 없다: groupBuyId=" + id));
        int oldOrganizerQuantity = organizerRow.getQuantity();
        int newOrganizerQuantity = r.organizerQuantity();
        int newSecured = g.getSecuredQuantity() - oldOrganizerQuantity + newOrganizerQuantity;
        Instant newDeadline = r.deadline().toInstant();

        List<ApiException.Field> errors = new ArrayList<>();
        if (newDeadline.isBefore(g.getDeadline())) {
            errors.add(new ApiException.Field("deadline", "마감 시각은 단축할 수 없습니다."));
        } else if (newDeadline.isAfter(g.getCreatedAt().plus(Duration.ofDays(MAX_DEADLINE_DAYS)))) {
            errors.add(new ApiException.Field("deadline", "마감 시각은 개설 시점으로부터 30일 이내여야 합니다."));
        }
        if (newSecured > r.totalQuantity()) {
            // 총 수량을 현재 확보 수량 아래로 내렸으면 총 수량 탓, 아니면 개설자 수량을 올린 탓으로 본다.
            boolean totalTooLow = r.totalQuantity() < g.getSecuredQuantity();
            errors.add(new ApiException.Field(totalTooLow ? "totalQuantity" : "organizerQuantity",
                    totalTooLow ? "총 수량은 현재 확보 수량 이상이어야 합니다."
                            : "개설자 수량이 너무 큽니다. 다른 참여자 수량과 합쳐 총 수량 이하여야 합니다."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.", errors);
        }

        // openChatUrl 은 알림 대상 변경에서 뺀다(모집 완료 전에는 참여자에게 공개하지 않는 값, FR-017).
        List<String> changed = new ArrayList<>();
        addIfChanged(changed, "title", g.getTitle(), r.title());
        addIfChanged(changed, "description", g.getDescription(), r.description());
        addIfChanged(changed, "itemName", g.getItemName(), r.itemName());
        addIfChanged(changed, "unitPrice", g.getUnitPrice(), r.unitPrice());
        addIfChanged(changed, "totalQuantity", g.getTotalQuantity(), r.totalQuantity());
        addIfChanged(changed, "deadline", g.getDeadline(), newDeadline);
        addIfChanged(changed, "organizerQuantity", oldOrganizerQuantity, newOrganizerQuantity);
        addIfChanged(changed, "category", g.getCategory(), r.category());
        addIfChanged(changed, "itemUrl", g.getItemUrl(), r.itemUrl());

        // 공구 행과 개설자 참여 행을 같은 트랜잭션에서 함께 갱신해 secured = Σ quantity 를 지킨다.
        g.update(r.title(), r.description(), r.itemName(), r.unitPrice(), r.totalQuantity(), newSecured,
                newDeadline, r.category(), r.itemUrl(), r.openChatUrl(), now);
        organizerRow.changeQuantity(newOrganizerQuantity);
        boolean completed = g.completeIfFull();

        List<Long> everyone = participations.findLockedByGroupBuyId(id).stream().map(Participation::getMemberId).toList();
        List<Long> others = everyone.stream().filter(m -> !m.equals(g.getOrganizerId())).toList();
        if (!changed.isEmpty() && !others.isEmpty()) {
            events.publishEvent(new GroupBuyChanged(id, others, changed));
        }
        if (completed) {
            completion.publish(id, everyone);
        }
        return GroupBuyResponse.from(g, newOrganizerQuantity, g.getOpenChatUrl());
    }

    @Transactional
    public void cancel(Long memberId, Long id) {
        Instant now = Instant.now(clock);
        GroupBuy g = lockModifiable(memberId, id, now);

        g.cancel(now);

        List<Long> others = participations.findLockedByGroupBuyId(id).stream().map(Participation::getMemberId)
                .filter(m -> !m.equals(g.getOrganizerId())).toList();
        if (!others.isEmpty()) {
            events.publishEvent(new GroupBuyCancelled(id, others));
        }
    }

    /** 행 락을 잡고 404(없음·취소됨) → 403(개설자 아님) → 409(OPEN·마감 전 아님) 순으로 거른다. */
    private GroupBuy lockModifiable(Long memberId, Long id, Instant now) {
        GroupBuy g = groupBuys.findByIdForUpdate(id)
                .filter(found -> found.getStatus() != GroupBuyStatus.CANCELLED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not Found", "공구를 찾을 수 없습니다."));
        if (!g.getOrganizerId().equals(memberId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "개설자만 할 수 있습니다.");
        }
        if (!g.isModifiable(now)) {
            String reason = g.getStatus() == GroupBuyStatus.OPEN ? "DEADLINE_PASSED" : "COMPLETED";
            throw ApiException.withReason(HttpStatus.CONFLICT, "NOT_MODIFIABLE",
                    "모집이 끝났거나 마감이 지나 수정·취소할 수 없습니다.", reason);
        }
        return g;
    }

    private static void addIfChanged(List<String> changed, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changed.add(field);
        }
    }

    /** 시계·다른 값에 의존하는 규칙. 위반은 모두 모아 400 으로 돌려준다. */
    private void validateForCreate(GroupBuyRequest r, Instant now) {
        List<ApiException.Field> errors = new ArrayList<>();
        Instant deadline = r.deadline().toInstant();
        if (!deadline.isAfter(now)) {
            errors.add(new ApiException.Field("deadline", "마감 시각은 현재보다 미래여야 합니다."));
        } else if (deadline.isAfter(now.plus(Duration.ofDays(MAX_DEADLINE_DAYS)))) {
            errors.add(new ApiException.Field("deadline", "마감 시각은 개설 시점으로부터 30일 이내여야 합니다."));
        }
        if (r.organizerQuantity() >= r.totalQuantity()) {
            errors.add(new ApiException.Field("organizerQuantity", "개설자 수량은 총 수량보다 작아야 합니다."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.", errors);
        }
    }
}
