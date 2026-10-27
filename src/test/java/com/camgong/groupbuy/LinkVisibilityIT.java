package com.camgong.groupbuy;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 19: contracts 의 openChatUrl 노출 표를 그대로 검증한다. */
class LinkVisibilityIT extends IntegrationTestBase {

    private static final String URL = "https://open.kakao.com/o/abc";

    private void expectLink(long groupBuyId, long viewer, boolean visible) throws Exception {
        var result = mockMvc.perform(get("/api/groupbuys/" + groupBuyId).with(as(viewer)))
                .andExpect(status().isOk());
        if (visible) {
            result.andExpect(jsonPath("$.openChatUrl").value(URL));
        } else {
            result.andExpect(jsonPath("$.openChatUrl").value(nullValue()));
        }
    }

    @Test
    void whileOpenOnlyTheOrganizerSeesTheLink() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 3);

        expectLink(id, ORGANIZER, true);
        expectLink(id, PARTICIPANT, false);
        expectLink(id, OUTSIDER, false);
    }

    @Test
    void afterCompletionOrganizerAndParticipantsSeeTheLink() throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 15);
        jdbc.update("UPDATE group_buy SET status = 'COMPLETED' WHERE id = ?", id);

        expectLink(id, ORGANIZER, true);
        expectLink(id, PARTICIPANT, true);
        expectLink(id, OUTSIDER, false);
    }
}
