package com.camgong.auth;

import static com.camgong.support.AuthBodies.login;
import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.AuthBodies.verify;
import static com.camgong.support.GroupBuyBodies.json;
import static com.camgong.support.GroupBuyBodies.valid;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 7, 19. 이후 기능이 추가되면 PROTECTED 에 한 줄씩 늘린다. */
class AuthGuardIT extends IntegrationTestBase {

    private static final List<Function<Long, MockHttpServletRequestBuilder>> PROTECTED = List.of(
            id -> get("/api/groupbuys/" + id),
            id -> post("/api/groupbuys").contentType(MediaType.APPLICATION_JSON).content(json(valid())),
            id -> put("/api/groupbuys/" + id).contentType(MediaType.APPLICATION_JSON).content(json(valid())),
            id -> post("/api/groupbuys/" + id + "/cancel"),
            id -> get("/api/auth/me"));

    @Test
    void everyProtectedEndpointRejectsRequestsWithoutAnySession() throws Exception {
        for (var request : PROTECTED) {
            mockMvc.perform(request.apply(1L)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.errors", hasSize(0)));
        }
    }

    @Test
    void forgedMemberHeaderAuthenticatesNobody() throws Exception {
        for (var request : PROTECTED) {
            mockMvc.perform(request.apply(1L).header("X-Member-Id", "1")).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void garbageOrUnknownCookieIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("camgong_session", "garbage")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validCookieWinsOverAForgedHeader() throws Exception {
        mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER)).header("X-Member-Id", "3")
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerId").value(ORGANIZER));
    }

    @Test
    void unauthenticatedBeatsValidationAndNotFound() throws Exception {
        mockMvc.perform(post("/api/groupbuys").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/groupbuys/999999").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authEntryPointsAreOpenWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(signup("open@school.ac.kr")))).andExpect(status().isAccepted());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(login("nobody@school.ac.kr", "password1"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.title").value("INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json(verify("no-such-token")))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void anAuthenticatedRequestDoesNotLeakIntoTheNextAnonymousOne() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(as(ORGANIZER))).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void trailingSlashAndCaseVariantsDoNotOpenProtectedPaths() throws Exception {
        mockMvc.perform(get("/api/auth/me/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/API/auth/me")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/auth/login/").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
