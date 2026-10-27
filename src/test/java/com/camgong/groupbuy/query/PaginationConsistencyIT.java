package com.camgong.groupbuy.query;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 5, 11 */
class PaginationConsistencyIT extends IntegrationTestBase {

    private String page(String query) throws Exception {
        return mockMvc.perform(get("/api/groupbuys" + query)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    private List<Long> ids(String body) {
        List<Number> raw = JsonPath.read(body, "$.items[*].id");
        return raw.stream().map(Number::longValue).toList();
    }

    private String next(String body) {
        return JsonPath.read(body, "$.nextCursor");
    }

    /** nextCursor 를 끝까지 따라가며 모든 id 를 모은다. */
    private List<Long> walk(String base, int expectedPages) throws Exception {
        List<Long> all = new ArrayList<>();
        String body = page(base);
        int pages = 1;
        all.addAll(ids(body));
        // 커서가 어긋나 같은 쪽을 맴돌아도 멈추도록 기대 쪽수보다 넉넉히만 돈다.
        while (next(body) != null && pages <= expectedPages) {
            body = page(base + "&cursor=" + next(body));
            all.addAll(ids(body));
            pages++;
        }
        assertThat(pages).isEqualTo(expectedPages);
        return all;
    }

    @Test
    void walkingAllPagesVisitsEveryGroupBuyExactlyOnceInBothSorts() throws Exception {
        List<Long> seeded = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            // 마감이 같은 공구가 많도록 5개씩 같은 시각
            seeded.add(groupBuy().title("공구" + i).deadline(START.plus(Duration.ofHours(1 + i / 5))).insert(jdbc));
        }
        List<Long> latest = walk("?size=20", 3);
        assertThat(latest).hasSize(45).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(seeded);
        assertThat(latest).isSortedAccordingTo(java.util.Comparator.reverseOrder());

        List<Long> byDeadline = walk("?size=20&sort=deadline", 3);
        assertThat(byDeadline).hasSize(45).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(seeded);
        assertThat(byDeadline).isEqualTo(seeded);          // 마감 이른 순, 같은 마감은 id 오름차순
    }

    @Test
    void sameDeadlineAcrossAPageBoundaryHasNoDuplicatesOrGaps() throws Exception {
        Instant same = START.plus(Duration.ofDays(2));
        List<Long> seeded = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            seeded.add(groupBuy().title("동시마감" + i).deadline(same).insert(jdbc));
        }
        assertThat(walk("?size=3&sort=deadline", 3)).isEqualTo(seeded);
    }

    @Test
    void lastPageHasNoNextCursorEvenWhenCountIsAnExactMultipleOfSize() throws Exception {
        for (int i = 0; i < 20; i++) {
            groupBuy().title("x" + i).insert(jdbc);
        }
        assertThat(next(page("?size=20"))).isNull();
        for (int i = 0; i < 1; i++) {
            groupBuy().title("y").insert(jdbc);
        }
        assertThat(next(page("?size=20"))).isNotNull();
    }

    @Test
    void changesAfterTheFirstPageNeverRepeatAlreadySeenGroupBuys() throws Exception {
        List<Long> seeded = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            seeded.add(groupBuy().title("공구" + i).insert(jdbc));
        }
        String first = page("?size=3");
        List<Long> seen = ids(first);

        long fresh = groupBuy().title("새 공구").insert(jdbc);                         // 최신순 맨 앞에 생긴다
        jdbc.update("UPDATE group_buy SET status = 'COMPLETED' WHERE id = ?", seeded.get(2));  // 아직 안 본 쪽의 상태 변화(기본 OPEN 목록에서 빠진다)
        jdbc.update("UPDATE group_buy SET status = 'CANCELLED' WHERE id = ?", seeded.get(1));  // 아직 안 본 쪽의 취소

        List<Long> rest = ids(page("?size=10&cursor=" + next(first)));
        assertThat(rest).doesNotContainAnyElementsOf(seen).doesNotContain(fresh, seeded.get(1));
        assertThat(rest).containsExactly(seeded.get(0));
    }

    @Test
    void microsecondDeadlinesAreWalkedWithoutDuplicatesOrGaps() throws Exception {
        List<Long> seeded = new ArrayList<>();
        Instant base = START.plus(Duration.ofDays(2));
        for (int i = 0; i < 12; i++) {
            // 마이크로초 단위로만 다른 마감과 완전히 같은 마감이 섞여 있다
            Instant deadline = base.plus(Duration.ofNanos(1000L * (i / 3) + 123_000));
            seeded.add(groupBuy().title("마이크로 " + i).deadline(deadline).insert(jdbc));
        }
        assertThat(walk("?size=5&sort=deadline", 3)).isEqualTo(seeded);
        assertThat(walk("?size=1&sort=deadline", 12)).isEqualTo(seeded);
    }
}
