package com.camgong.groupbuy;

import static com.camgong.support.GroupBuyBodies.json;
import static com.camgong.support.GroupBuyBodies.valid;
import static com.camgong.support.GroupBuyBodies.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.GroupBuyBodies;
import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 3~6, 21 + 경계값. 실패한 요청은 가드를 소모하지 않는다. */
class CreateGroupBuyValidationIT extends IntegrationTestBase {

    private ResultActions submit(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private void assertRejected(Map<String, Object> body, String... fields) throws Exception {
        ResultActions r = submit(body).andExpect(status().isBadRequest());
        for (String field : fields) {
            r.andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").exists());
        }
    }

    private void assertCreated(Map<String, Object> body) throws Exception {
        submit(body).andExpect(status().isCreated());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void pastDeadline() throws Exception {
        assertRejected(valid(START.minus(Duration.ofDays(1))), "deadline");
    }

    @Test
    void organizerQuantityMustBeLessThanTotal() throws Exception {
        assertRejected(with(with(valid(), "totalQuantity", 24), "organizerQuantity", 24), "organizerQuantity");
    }

    @Test
    void reportsEveryBrokenFieldAtOnce() throws Exception {
        assertRejected(with(with(valid(), "title", "   "), "totalQuantity", 0), "title", "totalQuantity");
    }

    @Test
    void categoryMissingOrUnknown() throws Exception {
        Map<String, Object> missing = valid();
        missing.remove("category");
        assertRejected(missing, "category");
        assertRejected(with(valid(), "category", "SNACK"), "category");
    }

    @Test
    void linksMustBeHttps() throws Exception {
        assertRejected(with(valid(), "openChatUrl", "ftp://x"), "openChatUrl");
        assertRejected(with(valid(), "openChatUrl", ""), "openChatUrl");
        Map<String, Object> noItemUrl = valid();
        noItemUrl.remove("itemUrl");
        assertRejected(noItemUrl, "itemUrl");
        assertRejected(with(valid(), "itemUrl", "http://example.com"), "itemUrl");
        assertRejected(with(valid(), "itemUrl", "https://x.com/" + "a".repeat(500)), "itemUrl");
    }

    @Test
    void totalQuantityBoundaries() throws Exception {
        assertRejected(with(with(valid(), "totalQuantity", 1), "organizerQuantity", 1), "totalQuantity");
        assertCreated(with(with(valid(), "totalQuantity", 2), "organizerQuantity", 1));
        assertCreated(with(valid(), "totalQuantity", 1000));
        assertRejected(with(valid(), "totalQuantity", 1001), "totalQuantity");
    }

    @Test
    void unitPriceBoundaries() throws Exception {
        assertRejected(with(valid(), "unitPrice", 0), "unitPrice");
        assertCreated(with(valid(), "unitPrice", 1));
        assertCreated(with(valid(), "unitPrice", 1_000_000));
        assertRejected(with(valid(), "unitPrice", 1_000_001), "unitPrice");
    }

    @Test
    void deadlineBoundaryIsThirtyDaysFromNow() throws Exception {
        Map<String, Object> exactly = with(valid(), "deadline", GroupBuyBodies.iso(START.plus(Duration.ofDays(30))));
        assertCreated(exactly);
        assertRejected(with(valid(), "deadline",
                GroupBuyBodies.iso(START.plus(Duration.ofDays(30)).plusSeconds(1))), "deadline");
    }

    @Test
    void textLengthBoundaries() throws Exception {
        assertCreated(with(with(valid(), "title", "가".repeat(100)), "itemName", "나".repeat(100)));
        assertRejected(with(valid(), "title", "가".repeat(101)), "title");
        assertRejected(with(valid(), "itemName", "나".repeat(101)), "itemName");
        assertRejected(with(valid(), "description", "다".repeat(2001)), "description");
    }

    @Test
    void failedRequestLeavesNoGuardAndResendIsStillBadRequest() throws Exception {
        Map<String, Object> bad = valid(START.minus(Duration.ofDays(1)));

        assertRejected(bad, "deadline");
        assertRejected(bad, "deadline"); // 409 가 아니라 같은 400

        assertThat(count("create_guard")).isZero();
        assertThat(count("group_buy")).isZero();
    }
}
