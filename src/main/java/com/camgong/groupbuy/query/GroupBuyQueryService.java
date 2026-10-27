package com.camgong.groupbuy.query;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.camgong.common.ApiException;
import com.camgong.groupbuy.GroupBuy;
import com.camgong.groupbuy.GroupBuyRepository;
import com.camgong.groupbuy.GroupBuyStatus;
import com.camgong.groupbuy.Participation;
import com.camgong.groupbuy.ParticipationRepository;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 읽기 전용 조회 흐름. 쓰기 쪽 GroupBuyService 와 분리한다. */
@Service
@Transactional(readOnly = true)
public class GroupBuyQueryService {

    // ponytail: 응답 오프셋은 KST 고정(001 응답과 같다). 다른 시간대 사용자가 생기면 요청별로 바꾼다.
    static final ZoneOffset KST = ZoneOffset.ofHours(9);

    public record Page<T>(List<T> items, String nextCursor) {
    }

    private final GroupBuySearchRepository search;
    private final GroupBuyRepository groupBuys;
    private final ParticipationRepository participations;
    private final Clock clock;

    public GroupBuyQueryService(GroupBuySearchRepository search, GroupBuyRepository groupBuys,
            ParticipationRepository participations, Clock clock) {
        this.search = search;
        this.groupBuys = groupBuys;
        this.participations = participations;
        this.clock = clock;
    }

    /**
     * 내 활동. role 은 organizer(내가 연 공구) / participant(내가 참여한 공구, 내가 연 공구 포함).
     * 두 탭 모두 공구 id 역순 키셋이고 CANCELLED 는 제외한다.
     */
    public Page<MyActivityItem> mine(Long memberId, String role, String cursor, String size) {
        List<ApiException.Field> errors = new ArrayList<>();
        if (!"organizer".equals(role) && !"participant".equals(role)) {
            errors.add(new ApiException.Field("role", "role 은 organizer 또는 participant 여야 합니다."));
        }
        int pageSize = GroupBuyListQuery.parseSize(size, errors);
        if (!errors.isEmpty()) {
            throw GroupBuyListQuery.badRequest(errors);
        }
        Long cursorId = cursor == null || cursor.isBlank() ? null
                : PageCursor.decode(cursor, GroupBuyListQuery.Sort.LATEST).id();
        Instant now = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        List<MyActivityItem> items = new ArrayList<>();
        if ("organizer".equals(role)) {
            for (GroupBuy g : groupBuys.findMine(memberId, cursorId, Limit.of(pageSize + 1))) {
                items.add(activityItem(g, now, null));
            }
        } else {
            for (Participation p : participations.findMine(memberId, cursorId, Limit.of(pageSize + 1))) {
                items.add(activityItem(p.getGroupBuy(), now, p.getQuantity()));
            }
        }
        boolean hasNext = items.size() > pageSize;
        List<MyActivityItem> page = hasNext ? items.subList(0, pageSize) : items;
        String nextCursor = hasNext ? PageCursor.latest(page.get(page.size() - 1).id()).encode() : null;
        return new Page<>(page, nextCursor);
    }

    private static MyActivityItem activityItem(GroupBuy g, Instant now, Integer myQuantity) {
        return new MyActivityItem(g.getId(), g.getTitle(), g.getItemName(), g.getCategory(), g.getUnitPrice(),
                g.getSecuredQuantity(), g.getTotalQuantity(), toOffset(g.getDeadline()),
                DisplayStatus.derive(g.getStatus(), g.getDeadline(), now), myQuantity);
    }

    /**
     * 상세. 오픈채팅 링크와 명단은 공개 규칙(data-model 공개 규칙 표, I8·I9)대로 서비스가 가린다.
     * 명단은 쿼리 자체를 개설자일 때만 실행한다.
     */
    public GroupBuyDetail detail(Long memberId, Long id) {
        Instant now = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        GroupBuy g = groupBuys.findDetailById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not Found", "공구를 찾을 수 없습니다."));
        DisplayStatus status = DisplayStatus.derive(g.getStatus(), g.getDeadline(), now);
        boolean isOrganizer = memberId.equals(g.getOrganizerId());

        Participation mine = participations.findByGroupBuyIdAndMemberId(id, memberId).orElse(null);
        MyParticipationView myParticipation = null;
        if (mine != null) {
            int amount = g.getUnitPrice() * mine.getQuantity();
            myParticipation = isOrganizer
                    ? new MyParticipationView.Organizer(mine.getQuantity(), amount)
                    : new MyParticipationView.Participant(mine.getQuantity(), amount, null, null);
        }
        int participantCount = (int) participations.countByGroupBuyId(id);

        List<ParticipantView> roster = null;
        if (isOrganizer) {
            roster = participations.findByGroupBuyIdOrderByIdAsc(id).stream()
                    .map(p -> new ParticipantView(p.getMemberId(), p.getMember().getName(), p.getQuantity())).toList();
        }
        return GroupBuyDetail.of(g, status, g.getOrganizer().getName(), isOrganizer, participantCount,
                myParticipation, visibleOpenChatUrl(g, status, isOrganizer, mine != null), roster);
    }

    /** 개설자: OPEN·PENDING_CLOSE·COMPLETED 에서 항상. 참여자: COMPLETED 에서만. 그 외는 숨긴다. */
    private static String visibleOpenChatUrl(GroupBuy g, DisplayStatus status, boolean isOrganizer, boolean participant) {
        if (isOrganizer) {
            return status == DisplayStatus.FAILED ? null : g.getOpenChatUrl();
        }
        return participant && status == DisplayStatus.COMPLETED ? g.getOpenChatUrl() : null;
    }

    public Page<GroupBuySummary> list(GroupBuyListQuery q) {
        // DB 마감은 마이크로초 정밀도라 같은 정밀도로 맞춰 derive 와 SQL 조건이 같은 경계를 보게 한다.
        Instant now = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        List<GroupBuySearchRepository.Row> rows = search.search(q, now);

        // size + 1 건 중 size 건만 돌려주고, 초과분이 있으면 마지막으로 돌려준 행에서 다음 커서를 만든다.
        boolean hasNext = rows.size() > q.size();
        List<GroupBuySearchRepository.Row> page = hasNext ? rows.subList(0, q.size()) : rows;
        String nextCursor = null;
        if (hasNext) {
            GroupBuySearchRepository.Row last = page.get(page.size() - 1);
            nextCursor = (q.sort() == GroupBuyListQuery.Sort.LATEST
                    ? PageCursor.latest(last.id())
                    : PageCursor.byDeadline(last.deadline(), last.id())).encode();
        }
        List<GroupBuySummary> items = page.stream().map(r -> new GroupBuySummary(r.id(), r.title(), r.itemName(),
                r.category(), r.unitPrice(), r.securedQuantity(), r.totalQuantity(), toOffset(r.deadline()),
                DisplayStatus.derive(r.status(), r.deadline(), now))).toList();
        return new Page<>(items, nextCursor);
    }

    static OffsetDateTime toOffset(Instant t) {
        return t.atOffset(KST);
    }
}
