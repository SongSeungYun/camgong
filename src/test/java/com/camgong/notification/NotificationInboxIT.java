package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

/** quickstart 시나리오 7, 8, 9, 10, 11 */
class NotificationInboxIT extends NotificationTestBase {

    @Test
    void theInboxListsOnlyMyNotificationsNewestFirstWithTheDocumentedShape() throws Exception {
        long a = insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);
        long b = insertNotification(PARTICIPANT, "GROUPBUY_CHANGED", START.plusSeconds(60), true);
        insertNotification(OUTSIDER, "GROUPBUY_COMPLETED", START.plusSeconds(120), false);          // 남의 알림

        mockMvc.perform(get("/api/notifications").with(as(PARTICIPANT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) b, (int) a)))
                .andExpect(jsonPath("$.items[0].type").value("GROUPBUY_CHANGED"))
                .andExpect(jsonPath("$.items[0].groupBuyId").value(1))
                .andExpect(jsonPath("$.items[0].groupBuyTitle").value("시드 알림"))
                .andExpect(jsonPath("$.items[0].changedFields[0]").value("unitPrice"))
                .andExpect(jsonPath("$.items[0].changedFields[1]").value("deadline"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-27T09:01:00+09:00"))
                .andExpect(jsonPath("$.items[0].read").value(true))
                .andExpect(jsonPath("$.items[0].linkable").value(true))
                .andExpect(jsonPath("$.items[1].read").value(false))
                .andExpect(jsonPath("$.items[1].changedFields").value((Object) null))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
    }

    @Test
    void keysetPagingVisitsEveryNotificationOnceAndPagingParametersAreValidated() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            ids.add(insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START.plusSeconds(i), false));
        }
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String body = mockMvc.perform(get("/api/notifications?size=3" + (cursor == null ? "" : "&cursor=" + cursor))
                    .with(as(PARTICIPANT))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(body, "$.items[*].id");
            page.forEach(n -> seen.add(n.longValue()));
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null && pages < 5);
        assertThat(pages).isEqualTo(3);
        assertThat(seen).hasSize(7).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(ids)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());

        mockMvc.perform(get("/api/notifications?size=0").with(as(PARTICIPANT))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("size")));
        mockMvc.perform(get("/api/notifications?size=51").with(as(PARTICIPANT))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notifications?cursor=garbage").with(as(PARTICIPANT))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("cursor")));
    }

    @Test
    void markingReadIsIdempotentAndKeepsTheFirstReadTime() throws Exception {
        long id = insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);
        mockMvc.perform(get("/api/notifications/unread-count").with(as(PARTICIPANT))).andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(put("/api/notifications/" + id + "/read").with(as(PARTICIPANT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.read").value(true));
        LocalDateTime first = jdbc.queryForObject("SELECT read_at FROM notification WHERE id = ?", LocalDateTime.class, id);
        assertThat(first).isEqualTo(LocalDateTime.ofInstant(START, ZoneOffset.UTC));

        clock.advance(Duration.ofMinutes(30));
        mockMvc.perform(put("/api/notifications/" + id + "/read").with(as(PARTICIPANT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        assertThat(jdbc.queryForObject("SELECT read_at FROM notification WHERE id = ?", LocalDateTime.class, id)).isEqualTo(first);
        mockMvc.perform(get("/api/notifications/unread-count").with(as(PARTICIPANT))).andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void theUnreadCountMatchesWhatIsUnread() throws Exception {
        insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);
        insertNotification(PARTICIPANT, "GROUPBUY_FAILED", START.plusSeconds(1), false);
        insertNotification(PARTICIPANT, "GROUPBUY_CHANGED", START.plusSeconds(2), true);
        insertNotification(OUTSIDER, "GROUPBUY_COMPLETED", START, false);

        mockMvc.perform(get("/api/notifications/unread-count").with(as(PARTICIPANT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
        mockMvc.perform(get("/api/notifications/unread-count").with(as(3L))).andExpect(jsonPath("$.unreadCount").value(1));
        mockMvc.perform(get("/api/notifications/unread-count").with(as(ORGANIZER))).andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void aCancelledNotificationIsNotLinkable() throws Exception {
        insertNotification(PARTICIPANT, "GROUPBUY_CANCELLED", START, false);
        mockMvc.perform(get("/api/notifications").with(as(PARTICIPANT))).andExpect(jsonPath("$.items[0].linkable").value(false));
    }

    @Test
    void anotherMembersNotificationIsA404IdenticalToAMissingOne() throws Exception {
        long mine = insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);

        String other = mockMvc.perform(put("/api/notifications/" + mine + "/read").with(as(OUTSIDER))).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(put("/api/notifications/99999/read").with(as(OUTSIDER))).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(other.replace("/api/notifications/" + mine + "/read", "X"))
                .isEqualTo(missing.replace("/api/notifications/99999/read", "X"));
        assertThat(jdbc.queryForObject("SELECT read_at FROM notification WHERE id = ?", LocalDateTime.class, mine)).isNull();
    }

    @Test
    void withoutLoginIs401() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/notifications/1/read")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications").header("X-Member-Id", "2")).andExpect(status().isUnauthorized());
    }
}
