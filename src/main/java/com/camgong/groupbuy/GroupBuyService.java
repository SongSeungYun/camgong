package com.camgong.groupbuy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.camgong.common.ApiException;
import com.camgong.member.MemberRepository;

@Service
public class GroupBuyService {

    private static final long MAX_DEADLINE_DAYS = 30;

    private final MemberRepository members;
    private final GroupBuyRepository groupBuys;
    private final ParticipationRepository participations;
    private final CreateGuard createGuard;
    private final Clock clock;

    public GroupBuyService(MemberRepository members, GroupBuyRepository groupBuys,
            ParticipationRepository participations, CreateGuard createGuard, Clock clock) {
        this.members = members;
        this.groupBuys = groupBuys;
        this.participations = participations;
        this.createGuard = createGuard;
        this.clock = clock;
    }

    @Transactional
    public GroupBuyResponse create(Long memberId, GroupBuyRequest r) {
        requireMember(memberId);
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

    @Transactional(readOnly = true)
    public GroupBuyResponse get(Long memberId, Long id) {
        requireMember(memberId);
        GroupBuy groupBuy = groupBuys.findById(id)
                .filter(g -> g.getStatus() != GroupBuyStatus.CANCELLED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not Found", "공구를 찾을 수 없습니다."));

        int organizerQuantity = participations
                .findByGroupBuyIdAndMemberId(id, groupBuy.getOrganizerId())
                .map(Participation::getQuantity).orElse(0);
        return GroupBuyResponse.from(groupBuy, organizerQuantity, visibleOpenChatUrl(memberId, groupBuy));
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

    /** 개설자는 OPEN·COMPLETED 에서, 참여자는 COMPLETED 에서만 링크를 본다. 그 외는 null. */
    private String visibleOpenChatUrl(Long memberId, GroupBuy g) {
        if (memberId.equals(g.getOrganizerId())) {
            return g.getOpenChatUrl();
        }
        boolean participant = participations.findByGroupBuyIdAndMemberId(g.getId(), memberId).isPresent();
        return participant && g.getStatus() == GroupBuyStatus.COMPLETED ? g.getOpenChatUrl() : null;
    }

    private void requireMember(Long memberId) {
        if (memberId == null || !members.existsById(memberId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "로그인이 필요합니다.");
        }
    }
}
