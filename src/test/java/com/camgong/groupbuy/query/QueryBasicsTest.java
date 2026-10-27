package com.camgong.groupbuy.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.camgong.common.ApiException;
import com.camgong.groupbuy.GroupBuyStatus;

/** DisplayStatus, PageCursor, GroupBuyListQuery 의 단위 동작(T005~T007). 쿼리 쪽 일치는 ListGroupBuyIT 가 본다. */
class QueryBasicsTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    @Test
    void displayStatusFollowsTheTable() {
        assertThat(DisplayStatus.derive(GroupBuyStatus.OPEN, NOW.plusSeconds(1), NOW)).isEqualTo(DisplayStatus.OPEN);
        assertThat(DisplayStatus.derive(GroupBuyStatus.OPEN, NOW, NOW)).isEqualTo(DisplayStatus.PENDING_CLOSE);
        assertThat(DisplayStatus.derive(GroupBuyStatus.OPEN, NOW.minusSeconds(1), NOW)).isEqualTo(DisplayStatus.PENDING_CLOSE);
        assertThat(DisplayStatus.derive(GroupBuyStatus.COMPLETED, NOW.minusSeconds(9), NOW)).isEqualTo(DisplayStatus.COMPLETED);
        assertThat(DisplayStatus.derive(GroupBuyStatus.FAILED, NOW.plusSeconds(9), NOW)).isEqualTo(DisplayStatus.FAILED);
        assertThatThrownBy(() -> DisplayStatus.derive(GroupBuyStatus.CANCELLED, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cursorRoundTripsAndKeepsMicrosecondDeadline() {
        Instant d = Instant.parse("2026-11-01T09:00:00.123456Z");
        PageCursor decoded = PageCursor.decode(PageCursor.byDeadline(d, 42).encode(), GroupBuyListQuery.Sort.DEADLINE);
        assertThat(decoded).isEqualTo(PageCursor.byDeadline(d, 42));
        assertThat(PageCursor.decode(PageCursor.latest(7).encode(), GroupBuyListQuery.Sort.LATEST))
                .isEqualTo(PageCursor.latest(7));
    }

    @Test
    void cursorForAnotherSortOrGarbageIsRejectedAsCursorError() {
        String latest = PageCursor.latest(7).encode();
        for (String bad : List.of(latest, "!!!", "", Base64.getUrlEncoder().encodeToString("{}".getBytes()),
                Base64.getUrlEncoder().encodeToString("[1]".getBytes()))) {
            assertThatThrownBy(() -> PageCursor.decode(bad, GroupBuyListQuery.Sort.DEADLINE))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrors()).extracting("field").containsExactly("cursor"));
        }
        // 마감 임박순 커서가 최신순 요청에 오는 경우도 거부
        String deadline = PageCursor.byDeadline(NOW, 1).encode();
        assertThatThrownBy(() -> PageCursor.decode(deadline, GroupBuyListQuery.Sort.LATEST)).isInstanceOf(ApiException.class);
    }

    @Test
    void cursorWithADateOutsideTheSupportedRangeIsRejected() {
        for (String d : List.of("+999999999-12-31T23:59:59Z", "-0001-01-01T00:00:00Z", "1999-12-31T23:59:59Z", "2100-01-01T00:00:01Z")) {
            String raw = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(("{\"s\":\"deadline\",\"i\":1,\"d\":\"" + d + "\"}").getBytes());
            assertThatThrownBy(() -> PageCursor.decode(raw, GroupBuyListQuery.Sort.DEADLINE)).as(d)
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void queryDefaultsAndParsing() {
        GroupBuyListQuery q = GroupBuyListQuery.of(null, null, null, null, null, null);
        assertThat(q.status()).isEqualTo(DisplayStatus.OPEN);
        assertThat(q.sort()).isEqualTo(GroupBuyListQuery.Sort.LATEST);
        assertThat(q.size()).isEqualTo(20);
        assertThat(q.words()).isEmpty();
        assertThat(GroupBuyListQuery.of(null, null, "  24   햇반 ", null, null, null).words()).containsExactly("24", "햇반");
        assertThat(GroupBuyListQuery.of(null, null, "   ", null, null, null).words()).isEmpty();
        assertThat(GroupBuyListQuery.of(null, null, "a b c d e f g", null, null, null).words()).hasSize(5);
    }

    @Test
    void invalidParametersAreCollectedByName() {
        assertThatThrownBy(() -> GroupBuyListQuery.of("CANCELLED", "SNACK", "가".repeat(51), "x", null, "100"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrors()).extracting("field")
                        .containsExactlyInAnyOrder("status", "category", "q", "sort", "size"));
        assertThatThrownBy(() -> GroupBuyListQuery.of(null, null, null, null, null, "0")).isInstanceOf(ApiException.class);
        assertThat(GroupBuyListQuery.of(null, null, "가".repeat(50), null, null, "50").size()).isEqualTo(50);
        assertThatThrownBy(() -> GroupBuyListQuery.of(null, null, null, "deadline", PageCursor.latest(1).encode(), null))
                .isInstanceOf(ApiException.class);
    }
}
