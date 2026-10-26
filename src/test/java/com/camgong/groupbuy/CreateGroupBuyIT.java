package com.camgong.groupbuy;

import static com.camgong.support.GroupBuyBodies.json;
import static com.camgong.support.GroupBuyBodies.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 1, 2 */
class CreateGroupBuyIT extends IntegrationTestBase {

    @Test
    void organizerOpensGroupBuyAndIsAutoJoined() throws Exception {
        String location = mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/groupbuys/\\d+")))
                .andExpect(jsonPath("$.securedQuantity").value(9))
                .andExpect(jsonPath("$.remainingQuantity").value(15))
                .andExpect(jsonPath("$.organizerQuantity").value(9))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.itemUrl").value("https://example.com/item/123"))
                .andExpect(jsonPath("$.openChatUrl").value("https://open.kakao.com/o/abc"))
                .andReturn().getResponse().getHeader("Location");

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT member_id, quantity FROM participation");
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("member_id")).longValue()).isEqualTo(ORGANIZER);
        assertThat(((Number) rows.get(0).get("quantity")).intValue()).isEqualTo(9);

        mockMvc.perform(get(location).with(as(ORGANIZER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securedQuantity").value(9))
                .andExpect(jsonPath("$.remainingQuantity").value(15))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void rejectsMissingHeaderAndUnknownMember() throws Exception {
        mockMvc.perform(post("/api/groupbuys").contentType(MediaType.APPLICATION_JSON).content(json(valid())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/groupbuys").with(as(99))
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid())))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_buy", Integer.class)).isZero();
    }
}
