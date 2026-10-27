package com.camgong.groupbuy.query;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 1, 2, 3, 4, 6, 7, 8, 9, 10 */
class ListGroupBuyIT extends IntegrationTestBase {

    private static final Instant SOON = START.plus(Duration.ofDays(1));
    private static final Instant LATER = START.plus(Duration.ofDays(5));

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get("/api/groupbuys" + query));
    }

    private long open(String title, String item) {
        return groupBuy().title(title).item(item).insert(jdbc);
    }

    @Test
    void anonymousListShowsOpenCardsWithTheDocumentedFieldsOnly() throws Exception {
        long id = groupBuy().title("햇반 24개 공구").item("햇반 210g").category("FOOD").price(1200).quantities(24, 9)
                .deadline(START.plus(Duration.ofDays(5)).plusSeconds(9 * 3600)).insert(jdbc);

        list("").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].title").value("햇반 24개 공구"))
                .andExpect(jsonPath("$.items[0].itemName").value("햇반 210g"))
                .andExpect(jsonPath("$.items[0].category").value("FOOD"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(1200))
                .andExpect(jsonPath("$.items[0].securedQuantity").value(9))
                .andExpect(jsonPath("$.items[0].totalQuantity").value(24))
                .andExpect(jsonPath("$.items[0].deadline").value("2026-11-01T18:00:00+09:00"))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].organizerId").doesNotExist())
                .andExpect(jsonPath("$.items[0].itemUrl").doesNotExist())
                .andExpect(jsonPath("$.items[0].openChatUrl").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
    }

    @Test
    void forgedMemberHeaderChangesNothingForTheList() throws Exception {
        open("a", "a");
        mockMvc.perform(get("/api/groupbuys").header("X-Member-Id", "999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void emptyResultIsAnEmptyListNotAnError() throws Exception {
        list("").andExpect(status().isOk()).andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
    }

    @Test
    void cancelledGroupBuysNeverAppearUnderAnyFilter() throws Exception {
        groupBuy().title("취소됨").status("CANCELLED").insert(jdbc);
        for (String s : new String[] { "", "?status=OPEN", "?status=PENDING_CLOSE", "?status=COMPLETED", "?status=FAILED",
                "?q=취소", "?sort=deadline" }) {
            list(s).andExpect(status().isOk()).andExpect(jsonPath("$.items", empty()));
        }
    }

    @Test
    void statusFilterUsesTheDerivedDisplayStatus() throws Exception {
        long open = groupBuy().title("모집중").deadline(SOON).insert(jdbc);
        long pending = groupBuy().title("마감됨").deadline(START.minusSeconds(1)).insert(jdbc);
        long boundary = groupBuy().title("딱마감").deadline(START).insert(jdbc);        // deadline = now → 마감됨
        long done = groupBuy().title("완료").status("COMPLETED").quantities(24, 24).insert(jdbc);
        long failed = groupBuy().title("실패").status("FAILED").deadline(START.minusSeconds(60)).insert(jdbc);

        list("").andExpect(jsonPath("$.items[*].id", contains((int) open)));
        list("?status=OPEN").andExpect(jsonPath("$.items[*].id", contains((int) open)));
        list("?status=PENDING_CLOSE").andExpect(jsonPath("$.items[*].id", contains((int) boundary, (int) pending)))
                .andExpect(jsonPath("$.items[0].status").value("PENDING_CLOSE"));
        list("?status=COMPLETED").andExpect(jsonPath("$.items[*].id", contains((int) done)))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"));
        list("?status=FAILED").andExpect(jsonPath("$.items[*].id", contains((int) failed)))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"));
    }

    @Test
    void latestIsDefaultAndDeadlineSortOrdersByDeadlineThenIdUnderEveryStatus() throws Exception {
        long a = groupBuy().title("a").deadline(LATER).insert(jdbc);
        long b = groupBuy().title("b").deadline(SOON).insert(jdbc);
        long c = groupBuy().title("c").deadline(SOON).insert(jdbc);
        list("").andExpect(jsonPath("$.items[*].id", contains((int) c, (int) b, (int) a)));
        list("?sort=latest").andExpect(jsonPath("$.items[*].id", contains((int) c, (int) b, (int) a)));
        list("?sort=deadline").andExpect(jsonPath("$.items[*].id", contains((int) b, (int) c, (int) a)));

        long p1 = groupBuy().title("p1").deadline(START.minusSeconds(10)).insert(jdbc);
        long p2 = groupBuy().title("p2").deadline(START.minusSeconds(100)).insert(jdbc);
        long p3 = groupBuy().title("p3").deadline(START.minusSeconds(100)).insert(jdbc);
        list("?status=PENDING_CLOSE&sort=deadline").andExpect(jsonPath("$.items[*].id", contains((int) p2, (int) p3, (int) p1)));

        long c1 = groupBuy().title("c1").status("COMPLETED").deadline(LATER).insert(jdbc);
        long c2 = groupBuy().title("c2").status("COMPLETED").deadline(SOON).insert(jdbc);
        list("?status=COMPLETED&sort=deadline").andExpect(jsonPath("$.items[*].id", contains((int) c2, (int) c1)));
    }

    @Test
    void categoryFilter() throws Exception {
        long food = groupBuy().title("식품").category("FOOD").insert(jdbc);
        groupBuy().title("생활").category("DAILY").insert(jdbc);
        list("?category=FOOD").andExpect(jsonPath("$.items[*].id", contains((int) food)));
        list("?category=ETC").andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void searchMatchesPartialWordsInTitleOrItemNameWithAllWordsRequired() throws Exception {
        long rice = open("햇반 24개 공구", "즉석밥");
        long item = open("아침 대용", "컵햇반 소");
        open("라면 공구", "신라면 5개");

        list("?q=햇").andExpect(jsonPath("$.items[*].id", contains((int) item, (int) rice)));
        list("?q=햇반").andExpect(jsonPath("$.items[*].id", contains((int) item, (int) rice)));
        list("?q=24 햇반").andExpect(jsonPath("$.items[*].id", contains((int) rice)));
        list("?q=  24   햇반  ").andExpect(jsonPath("$.items[*].id", contains((int) rice)));
        list("?q=24 라면").andExpect(jsonPath("$.items", empty()));
        list("?q=   ").andExpect(jsonPath("$.items", hasSize(3)));    // 공백뿐이면 검색 안 함
        list("?q=SHIN").andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void searchIsCaseInsensitiveForLatin() throws Exception {
        long id = open("Instant Rice", "x");
        list("?q=instant").andExpect(jsonPath("$.items[*].id", contains((int) id)));
        list("?q=RICE").andExpect(jsonPath("$.items[*].id", contains((int) id)));
    }

    @Test
    void wildcardCharactersInSearchAreLiteral() throws Exception {
        long percent = open("100% 정품", "x");
        long underscore = open("a_b", "x");
        open("abc", "x");

        list("?q=%").andExpect(jsonPath("$.items[*].id", contains((int) percent)));
        list("?q=_").andExpect(jsonPath("$.items[*].id", contains((int) underscore)));
        // 쿼리 문자열로 넣으면 MockMvc 가 %5C 를 디코딩하지 않으므로 파라미터로 넣는다.
        mockMvc.perform(get("/api/groupbuys").param("q", "\\")).andExpect(jsonPath("$.items", empty()));
        long backslash = open("경로 c:\\temp", "x");
        mockMvc.perform(get("/api/groupbuys").param("q", "\\"))
                .andExpect(jsonPath("$.items[*].id", contains((int) backslash)));
    }

    @Test
    void invalidParametersAreRejectedWithTheParameterName() throws Exception {
        list("?sort=x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("sort")));
        list("?size=100").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("size")));
        list("?size=0").andExpect(status().isBadRequest());
        list("?size=abc").andExpect(status().isBadRequest());
        list("?q=" + "가".repeat(51)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("q")));
        list("?status=CANCELLED").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("status")));
        list("?category=SNACK").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("category")));
        list("?cursor=garbage").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("cursor")));
        list("?q=" + "가".repeat(50)).andExpect(status().isOk());
        list("?size=50").andExpect(status().isOk());
    }

    @Test
    void cursorFromAnotherSortIsRejected() throws Exception {
        for (int i = 0; i < 3; i++) {
            open("t" + i, "x");
        }
        String cursor = com.jayway.jsonpath.JsonPath.read(
                list("?size=1").andReturn().getResponse().getContentAsString(), "$.nextCursor");
        list("?size=1&sort=deadline&cursor=" + cursor).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("cursor")));
    }

    @Test
    void filtersCombine() throws Exception {
        long hit = groupBuy().title("햇반 묶음").category("FOOD").status("COMPLETED").deadline(SOON).insert(jdbc);
        groupBuy().title("햇반 묶음").category("DAILY").status("COMPLETED").insert(jdbc);
        groupBuy().title("햇반 묶음").category("FOOD").insert(jdbc);
        groupBuy().title("라면").category("FOOD").status("COMPLETED").insert(jdbc);
        long later = groupBuy().title("햇반 둘").category("FOOD").status("COMPLETED").deadline(LATER).insert(jdbc);

        list("?status=COMPLETED&category=FOOD&q=햇반&sort=deadline")
                .andExpect(jsonPath("$.items[*].id", contains((int) hit, (int) later)));
    }
}
