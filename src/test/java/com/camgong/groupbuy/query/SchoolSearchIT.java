package com.camgong.groupbuy.query;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** 009 T009: GET /api/schools?q= — 공구가 있는 학교 키 가운데 검색어가 들어간 것, 사전순 최대 10개. */
class SchoolSearchIT extends IntegrationTestBase {

    private long nextMember = 100;

    private void schoolWithGroupBuy(String school, String status) {
        long id = nextMember++;
        seedMember(id, "회원" + id, school);
        groupBuy().organizer(id).status(status).insert(jdbc);
    }

    @BeforeEach
    void schools() {
        schoolWithGroupBuy("snue.ac.kr", "OPEN");
        schoolWithGroupBuy("snu.ac.kr", "OPEN");
        schoolWithGroupBuy("snu.ac.kr", "COMPLETED");
        schoolWithGroupBuy("korea.ac.kr", "FAILED");
        schoolWithGroupBuy("skku.edu", "OPEN");
        schoolWithGroupBuy("cancelled.ac.kr", "CANCELLED");   // 취소된 공구뿐인 학교는 목록에서 빈 결과라 나오지 않는다
        seedMember(nextMember++, "공구없음", "empty.ac.kr");     // 회원만 있고 공구가 없는 학교
    }

    /** q 를 그대로(인코딩은 MockMvc 가) 보낸다. null 이면 q 없이. */
    private ResultActions search(String q) throws Exception {
        var request = get("/api/schools");
        return mockMvc.perform(q == null ? request : request.param("q", q));
    }

    @Test
    void partialMatchCaseInsensitiveSortedWithoutLogin() throws Exception {
        search("snu").andExpect(status().isOk()).andExpect(jsonPath("$.items", contains("snu.ac.kr", "snue.ac.kr")));
        search("SNU").andExpect(jsonPath("$.items", contains("snu.ac.kr", "snue.ac.kr")));
        search("  snue ").andExpect(jsonPath("$.items", contains("snue.ac.kr")));
        search("ac.kr").andExpect(jsonPath("$.items", contains("korea.ac.kr", "snu.ac.kr", "snue.ac.kr")));
        search("edu").andExpect(jsonPath("$.items", contains("skku.edu")));
        search("school.ac.kr").andExpect(jsonPath("$.items", hasSize(0)));     // 기본 시드 회원들은 공구가 없다
        search("empty").andExpect(jsonPath("$.items", hasSize(0)));
        search("cancelled").andExpect(jsonPath("$.items", hasSize(0)));
        search("nothing").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void likeWildcardsAreLiteral() throws Exception {
        search("%").andExpect(jsonPath("$.items", hasSize(0)));
        search("_").andExpect(jsonPath("$.items", hasSize(0)));
        search("s_u").andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void atMostTenInDictionaryOrder() throws Exception {
        for (int i = 12; i >= 1; i--) {
            schoolWithGroupBuy("zz%02d.ac.kr".formatted(i), "OPEN");
        }
        search("zz").andExpect(jsonPath("$.items", contains("zz01.ac.kr", "zz02.ac.kr", "zz03.ac.kr", "zz04.ac.kr",
                "zz05.ac.kr", "zz06.ac.kr", "zz07.ac.kr", "zz08.ac.kr", "zz09.ac.kr", "zz10.ac.kr")));
    }

    @Test
    void missingBlankOrTooLongQueryIs400() throws Exception {
        search(null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("q"));
        search("").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("q"));
        search("  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("q"));
        search("a".repeat(51)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("q"));
        search("a".repeat(50)).andExpect(status().isOk());
    }
}
