package com.camgong.groupbuy.query;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.camgong.support.GroupBuyBodies;
import com.camgong.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/** 009 US2: 학교 범위(목록·검색·상세·참여)와 비로그인 전국 목록. 기본 시드 회원 1~3 은 school.ac.kr 이다. */
class SchoolScopeIT extends IntegrationTestBase {

    private static final String OTHER = "other.ac.kr";
    private static final long OTHER_ORGANIZER = 10L;
    private static final long OTHER_MEMBER = 11L;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private long a1;
    private long a2;
    private long b1;
    private long b2;
    private long b3;

    @BeforeEach
    void twoSchools() {
        seedMember(OTHER_ORGANIZER, "다른학교개설", OTHER);
        seedMember(OTHER_MEMBER, "다른학교회원", OTHER);
        a1 = groupBuy().title("우리 햇반").insert(jdbc);
        a2 = groupBuy().title("우리 생수").category("DAILY").insert(jdbc);
        b1 = groupBuy().organizer(OTHER_ORGANIZER).title("저쪽 라면").insert(jdbc);
        b2 = groupBuy().organizer(OTHER_ORGANIZER).title("저쪽 휴지").category("DAILY").insert(jdbc);
        b3 = groupBuy().organizer(OTHER_ORGANIZER).title("저쪽 과자").insert(jdbc);
    }

    private ResultActions list(String query, long asMember) throws Exception {
        return mockMvc.perform(get("/api/groupbuys" + query).with(as(asMember)));
    }

    private ResultActions anonymous(String query) throws Exception {
        return mockMvc.perform(get("/api/groupbuys" + query));
    }

    private static Integer[] ids(long... values) {
        Integer[] out = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (int) values[i];
        }
        return out;
    }

    // --- T006 ---

    @Test
    void aNewGroupBuyBelongsToTheOrganizersSchool() throws Exception {
        long mine = openGroupBuy();
        String location = mockMvc.perform(post("/api/groupbuys").with(as(OTHER_ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content(GroupBuyBodies.json(GroupBuyBodies.valid())))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        long theirs = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        assertThat(jdbc.queryForObject("SELECT school FROM group_buy WHERE id = ?", String.class, mine)).isEqualTo(SCHOOL);
        assertThat(jdbc.queryForObject("SELECT school FROM group_buy WHERE id = ?", String.class, theirs)).isEqualTo(OTHER);
    }

    // --- T007 ---

    @Test
    void aMemberSeesOnlyTheirSchoolAndTheSchoolParameterIsIgnored() throws Exception {
        list("", PARTICIPANT).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(a1, a2))))
                .andExpect(jsonPath("$.items[*].school", everyItem(is(SCHOOL))));
        list("?school=" + OTHER, PARTICIPANT).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(a1, a2))));
        list("", OTHER_MEMBER).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1, b2, b3))))
                .andExpect(jsonPath("$.items[*].school", everyItem(is(OTHER))));
    }

    @Test
    void searchingForAnotherSchoolsWordFindsNothing() throws Exception {
        list("?q=라면", PARTICIPANT).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
        list("?q=라면", OTHER_MEMBER).andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1))));
    }

    @Test
    void anonymousListIsNationwideWithTheSchoolOnEachCard() throws Exception {
        anonymous("").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(a1, a2, b1, b2, b3))));
        anonymous("?school=" + OTHER).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1, b2, b3))))
                .andExpect(jsonPath("$.items[*].school", everyItem(is(OTHER))));
        anonymous("?school=OTHER.AC.KR").andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1, b2, b3))));
        anonymous("?school=nowhere.ac.kr").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
        anonymous("?school=").andExpect(jsonPath("$.items", hasSize(5)));
    }

    @Test
    void anonymousSchoolFilterCombinesWithCategoryWordsAndSort() throws Exception {
        anonymous("?school=" + OTHER + "&category=DAILY").andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b2))));
        anonymous("?school=" + OTHER + "&q=과자").andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b3))));
        anonymous("?school=" + SCHOOL + "&sort=deadline").andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(a1, a2))));
    }

    @Test
    void pagingStaysInsideTheSchool() throws Exception {
        String body = anonymous("?school=" + OTHER + "&size=2").andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(body, "$.nextCursor");
        anonymous("?school=" + OTHER + "&size=2&cursor=" + cursor).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1))))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));

        String memberBody = list("?size=1", PARTICIPANT).andReturn().getResponse().getContentAsString();
        String memberCursor = JsonPath.read(memberBody, "$.nextCursor");
        list("?size=1&cursor=" + memberCursor, PARTICIPANT).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(a1))));
    }

    @Test
    void schoolLongerThan253CharactersIs400() throws Exception {
        anonymous("?school=" + "a".repeat(254)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("school"));
        list("?school=" + "a".repeat(254), PARTICIPANT).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("school"));
        anonymous("?school=" + "a".repeat(253)).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    // --- T008 ---

    /** 응답 본문에서 요청마다 다른 instance(경로)만 빼고 비교한다. */
    private Map<?, ?> problem(ResultActions r) throws Exception {
        Map<?, ?> body = JSON.readValue(r.andReturn().getResponse().getContentAsString(), Map.class);
        body.remove("instance");
        return body;
    }

    private void sameAsMissing(String method, String pathTemplate, String body) throws Exception {
        long missing = 999_999L;
        ResultActions other = mockMvc.perform(request(method, pathTemplate.formatted(b1), body).with(as(PARTICIPANT)))
                .andExpect(status().isNotFound());
        ResultActions none = mockMvc.perform(request(method, pathTemplate.formatted(missing), body).with(as(PARTICIPANT)))
                .andExpect(status().isNotFound());
        assertThat(problem(other)).isEqualTo(problem(none));
    }

    private static MockHttpServletRequestBuilder request(String method, String path, String body) {
        MockHttpServletRequestBuilder b = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void anotherSchoolsGroupBuyLooksExactlyLikeAMissingOne() throws Exception {
        String join = "{\"quantity\":1,\"expectedUnitPrice\":1200}";
        sameAsMissing("GET", "/api/groupbuys/%d", null);
        sameAsMissing("POST", "/api/groupbuys/%d/participations", join);
        sameAsMissing("PUT", "/api/groupbuys/%d/participations/me", join);
        sameAsMissing("DELETE", "/api/groupbuys/%d/participations/me", null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT secured_quantity FROM group_buy WHERE id = ?", Integer.class, b1)).isEqualTo(9);
    }

    @Test
    void sameSchoolStillWorks() throws Exception {
        mockMvc.perform(get("/api/groupbuys/" + b1).with(as(OTHER_MEMBER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.school").value(OTHER));
        mockMvc.perform(post("/api/groupbuys/" + b1 + "/participations").with(as(OTHER_MEMBER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1,\"expectedUnitPrice\":1200}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/groupbuys/" + a1).with(as(PARTICIPANT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.school").value(SCHOOL));
    }
}
