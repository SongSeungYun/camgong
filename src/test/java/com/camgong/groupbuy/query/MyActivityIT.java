package com.camgong.groupbuy.query;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 18, 19, 20 */
class MyActivityIT extends IntegrationTestBase {

    private ResultActions mine(long viewer, String query) throws Exception {
        return mockMvc.perform(get("/api/me/groupbuys" + query).with(as(viewer)));
    }

    private void participate(long groupBuyId, long memberId, int qty) {
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, ?, ?)",
                groupBuyId, memberId, qty, LocalDateTime.ofInstant(START, ZoneOffset.UTC));
    }

    /** 회원이 직접 연 공구(참여 행 포함) */
    private long opened(long organizer, String title) {
        long id = groupBuy().organizer(organizer).title(title).insert(jdbc);
        participate(id, organizer, 3);
        return id;
    }

    @Test
    void organizerAndParticipantTabsSplitCorrectlyNewestFirst() throws Exception {
        long own1 = opened(PARTICIPANT, "내가 연 1");
        long other1 = opened(ORGANIZER, "남이 연 1");
        participate(other1, PARTICIPANT, 2);
        long own2 = opened(PARTICIPANT, "내가 연 2");
        long other2 = opened(ORGANIZER, "남이 연 2");
        participate(other2, PARTICIPANT, 4);
        long other3 = opened(OUTSIDER, "남이 연 3");
        participate(other3, PARTICIPANT, 5);
        long unrelated = opened(OUTSIDER, "무관");

        mine(PARTICIPANT, "?role=organizer").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) own2, (int) own1)))
                .andExpect(jsonPath("$.items[0].title").value("내가 연 2"))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].myQuantity").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("myQuantity"))))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));

        mine(PARTICIPANT, "?role=participant").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) other3, (int) other2, (int) own2, (int) other1, (int) own1)))
                .andExpect(jsonPath("$.items[0].myQuantity").value(5))
                .andExpect(jsonPath("$.items[2].myQuantity").value(3))        // 내가 연 공구도 내 수량이 붙는다
                .andExpect(jsonPath("$.items[0].itemName").value("시드 상품"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(1200))
                .andExpect(jsonPath("$.items[0].organizerId").doesNotExist())
                .andExpect(jsonPath("$.items[*].id", org.hamcrest.Matchers.not(hasItem((int) unrelated))));
    }

    @Test
    void showsDisplayStatusBadgeIncludingPendingCloseAndFailed() throws Exception {
        long pending = groupBuy().organizer(PARTICIPANT).deadline(START.minusSeconds(1)).insert(jdbc);
        long failed = groupBuy().organizer(PARTICIPANT).status("FAILED").deadline(START.minusSeconds(1)).insert(jdbc);
        long done = groupBuy().organizer(PARTICIPANT).status("COMPLETED").insert(jdbc);

        mine(PARTICIPANT, "?role=organizer")
                .andExpect(jsonPath("$.items[*].id", contains((int) done, (int) failed, (int) pending)))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[1].status").value("FAILED"))
                .andExpect(jsonPath("$.items[2].status").value("PENDING_CLOSE"));
    }

    @Test
    void cancelledGroupBuysDisappearFromBothTabs() throws Exception {
        long id = opened(ORGANIZER, "곧 취소");
        participate(id, PARTICIPANT, 2);
        mine(PARTICIPANT, "?role=participant").andExpect(jsonPath("$.items[*].id", contains((int) id)));
        mine(ORGANIZER, "?role=organizer").andExpect(jsonPath("$.items[*].id", contains((int) id)));

        jdbc.update("UPDATE group_buy SET status = 'CANCELLED' WHERE id = ?", id);

        mine(PARTICIPANT, "?role=participant").andExpect(jsonPath("$.items", empty()));
        mine(ORGANIZER, "?role=organizer").andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void memberWithoutActivityGetsEmptyLists() throws Exception {
        mine(OUTSIDER, "?role=organizer").andExpect(status().isOk()).andExpect(jsonPath("$.items", empty()));
        mine(OUTSIDER, "?role=participant").andExpect(status().isOk()).andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void withoutLoginIs401() throws Exception {
        mockMvc.perform(get("/api/me/groupbuys?role=organizer")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/groupbuys?role=organizer").header("X-Member-Id", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRoleSizeOrCursorIs400() throws Exception {
        mine(ORGANIZER, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("role")));
        mine(ORGANIZER, "?role=x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("role")));
        mine(ORGANIZER, "?role=ORGANIZER").andExpect(status().isBadRequest());
        mine(ORGANIZER, "?role=organizer&size=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("size")));
        mine(ORGANIZER, "?role=organizer&size=51").andExpect(status().isBadRequest());
        mine(ORGANIZER, "?role=organizer&cursor=garbage").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("cursor")));
    }

    @Test
    void keysetPagingVisitsEveryItemOnceInBothTabs() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            ids.add(opened(PARTICIPANT, "내 공구 " + i));
        }
        for (String role : new String[] { "organizer", "participant" }) {
            List<Long> seen = new ArrayList<>();
            String url = "?role=" + role + "&size=3";
            int pages = 0;
            String cursor = null;
            do {
                String body = mine(PARTICIPANT, url + (cursor == null ? "" : "&cursor=" + cursor)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                List<Number> page = JsonPath.read(body, "$.items[*].id");
                page.forEach(n -> seen.add(n.longValue()));
                cursor = JsonPath.read(body, "$.nextCursor");
                pages++;
            } while (cursor != null && pages < 5);
            assertThat(pages).as(role).isEqualTo(3);
            assertThat(seen).as(role).hasSize(7).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(ids)
                    .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        }
    }

    @Test
    void doesNotLeakOtherMembersActivity() throws Exception {
        opened(ORGANIZER, "개설자의 공구");
        mine(PARTICIPANT, "?role=organizer").andExpect(jsonPath("$.items", empty()));
        mine(PARTICIPANT, "?role=participant").andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void participantPagingAcrossOthersGroupBuysWithGapsNeverRepeatsOrSkips() throws Exception {
        List<Long> joined = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            long id = opened(ORGANIZER, "남의 공구 " + i);
            opened(OUTSIDER, "참여 안 한 공구 " + i);      // 사이사이에 내가 참여하지 않은 공구가 끼어 있다
            participate(id, PARTICIPANT, 1);
            joined.add(id);
        }
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String body = mine(PARTICIPANT, "?role=participant&size=4" + (cursor == null ? "" : "&cursor=" + cursor))
                    .andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(body, "$.items[*].id");
            page.forEach(n -> seen.add(n.longValue()));
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null && pages < 5);
        assertThat(pages).isEqualTo(2);
        assertThat(seen).containsExactlyElementsOf(joined.reversed());
    }

    @Test
    void exactlyPageSizeItemsHaveNoNextCursor() throws Exception {
        for (int i = 0; i < 3; i++) {
            opened(PARTICIPANT, "딱 세 건 " + i);
        }
        for (String role : new String[] { "organizer", "participant" }) {
            mine(PARTICIPANT, "?role=" + role + "&size=3").andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.nextCursor").value((Object) null));
            mine(PARTICIPANT, "?role=" + role + "&size=2").andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.nextCursor").isNotEmpty());
        }
    }

    @Test
    void aDeadlineSortCursorFromTheListIsRejected() throws Exception {
        for (int i = 0; i < 3; i++) {
            groupBuy().title("t" + i).deadline(START.plusSeconds(3600L * (i + 1))).insert(jdbc);
        }
        String cursor = JsonPath.read(mockMvc.perform(get("/api/groupbuys?size=1&sort=deadline")).andReturn()
                .getResponse().getContentAsString(), "$.nextCursor");
        mine(PARTICIPANT, "?role=organizer&cursor=" + cursor).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("cursor")));
        mine(PARTICIPANT, "?role=participant&cursor=" + cursor).andExpect(status().isBadRequest());
    }
}
