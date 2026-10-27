package com.camgong.auth;

import static com.camgong.support.GroupBuyBodies.json;
import static com.camgong.support.GroupBuyBodies.valid;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.camgong.support.IntegrationTestBase;

/** contract 의 CSRF 절: Origin/Referer 가 같은 출처인지 확인하고, 둘 다 없으면 통과(tasks 표 c) */
class OriginCheckIT extends IntegrationTestBase {

    private static final String BODY = json(valid());

    @Test
    void foreignOriginIsForbiddenOnStateChangingRequests() throws Exception {
        mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER)).header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("FORBIDDEN_ORIGIN"));
        mockMvc.perform(put("/api/groupbuys/1").with(as(ORGANIZER)).header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/groupbuys/1/cancel").with(as(ORGANIZER)).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sameOriginPasses() throws Exception {
        mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER)).header("Origin", "http://localhost")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void refererIsUsedWhenThereIsNoOrigin() throws Exception {
        mockMvc.perform(post("/api/groupbuys/1/cancel").with(as(ORGANIZER)).header("Referer", "https://evil.example/page"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER)).header("Referer", "http://localhost/groupbuys/new")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void neitherOriginNorRefererPasses() throws Exception {
        mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void readRequestsAreNotChecked() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(as(ORGANIZER)).header("Origin", "https://evil.example"))
                .andExpect(status().isOk());
    }

    @Test
    void foreignOriginIsAlsoForbiddenOnAuthEndpoints() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonJsonBodiesAreRejected() throws Exception {
        mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER))
                        .contentType(MediaType.TEXT_PLAIN).content(BODY))
                .andExpect(status().isUnsupportedMediaType());
    }
}
