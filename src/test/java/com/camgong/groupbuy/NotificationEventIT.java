package com.camgong.groupbuy;

import static com.camgong.support.GroupBuyBodies.valid;
import static com.camgong.support.GroupBuyBodies.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 20 (+14 의 GroupBuyCompleted) */
class NotificationEventIT extends IntegrationTestBase {

    @Test
    void changeAndCancelNotifyParticipantsExceptTheOrganizer() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 2);
        seedParticipant(id, OUTSIDER, 2);

        updateAs(ORGANIZER, id, with(valid(), "unitPrice", 1500)).andExpect(status().isOk());

        assertThat(events.changed()).hasSize(1);
        assertThat(events.changed().get(0).groupBuyId()).isEqualTo(id);
        assertThat(events.changed().get(0).recipientIds()).containsExactlyInAnyOrder(PARTICIPANT, OUTSIDER);
        assertThat(events.changed().get(0).changedFields()).containsExactly("unitPrice");

        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        assertThat(events.cancelled()).hasSize(1);
        assertThat(events.cancelled().get(0).recipientIds()).containsExactlyInAnyOrder(PARTICIPANT, OUTSIDER);
    }

    @Test
    void changingOnlyTheOpenChatLinkPublishesNothing() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 2);

        updateAs(ORGANIZER, id, with(valid(), "openChatUrl", "https://open.kakao.com/o/changed"))
                .andExpect(status().isOk());

        assertThat(events.changed()).isEmpty();
    }

    @Test
    void changingTheItemLinkIsNotified() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 2);

        updateAs(ORGANIZER, id, with(valid(), "itemUrl", "https://example.com/other")).andExpect(status().isOk());

        assertThat(events.changed()).hasSize(1);
        assertThat(events.changed().get(0).changedFields()).containsExactly("itemUrl");
    }

    @Test
    void nothingIsPublishedWhenOnlyTheOrganizerParticipates() throws Exception {
        long id = openGroupBuy();

        updateAs(ORGANIZER, id, with(valid(), "unitPrice", 1500)).andExpect(status().isOk());
        cancelAs(ORGANIZER, id).andExpect(status().isNoContent());

        assertThat(events.changed()).isEmpty();
        assertThat(events.cancelled()).isEmpty();
    }

    @Test
    void rolledBackUpdatePublishesNothing() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 2);

        updateAs(ORGANIZER, id, valid(START.plus(Duration.ofDays(6)))) // 마감 단축 → 400
                .andExpect(status().isBadRequest());

        assertThat(events.changed()).isEmpty();
        assertThat(events.completed()).isEmpty();
    }

    @Test
    void fillingExactlyPublishesCompletedToEveryoneIncludingTheOrganizer() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 6);

        updateAs(ORGANIZER, id, with(valid(), "organizerQuantity", 18)).andExpect(status().isOk());

        assertThat(events.completed()).hasSize(1);
        assertThat(events.completed().get(0).recipientIds()).containsExactlyInAnyOrder(ORGANIZER, PARTICIPANT);
    }
}
