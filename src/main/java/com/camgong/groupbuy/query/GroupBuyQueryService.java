package com.camgong.groupbuy.query;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
    private final Clock clock;

    public GroupBuyQueryService(GroupBuySearchRepository search, Clock clock) {
        this.search = search;
        this.clock = clock;
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
