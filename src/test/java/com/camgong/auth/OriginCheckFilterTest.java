package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

/** Origin/Referer 비교 규칙 단위 테스트. 서버는 https://camgong.example(443)로 보이는 요청이다. */
class OriginCheckFilterTest {

    private int status(List<String> allowed, String origin, String referer) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/groupbuys");
        request.setScheme("https");
        request.setServerName("camgong.example");
        request.setServerPort(443);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        if (referer != null) {
            request.addHeader("Referer", referer);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new OriginCheckFilter(allowed, JsonMapper.builder().build()).doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void sameOriginPassesWhateverTheCaseOrExplicitDefaultPort() throws Exception {
        assertThat(status(List.of(), "https://camgong.example", null)).isEqualTo(200);
        assertThat(status(List.of(), "https://camgong.example:443", null)).isEqualTo(200);
        assertThat(status(List.of(), "HTTPS://CamGong.Example", null)).isEqualTo(200);
        assertThat(status(List.of(), null, "https://camgong.example:443/groupbuys?x=1")).isEqualTo(200);
        assertThat(status(List.of(), null, "https://CAMGONG.example/a")).isEqualTo(200);
    }

    @Test
    void differentSchemePortHostOrNullOriginIsForbidden() throws Exception {
        assertThat(status(List.of(), "http://camgong.example", null)).isEqualTo(403);
        assertThat(status(List.of(), "https://camgong.example:8443", null)).isEqualTo(403);
        assertThat(status(List.of(), "https://camgong.example.evil.com", null)).isEqualTo(403);
        assertThat(status(List.of(), "null", null)).isEqualTo(403);
        assertThat(status(List.of(), null, "not a url")).isEqualTo(403);
    }

    @Test
    void userinfoInRefererDoesNotChangeTheOrigin() throws Exception {
        assertThat(status(List.of(), null, "https://camgong.example@evil.com/")).isEqualTo(403);
    }

    @Test
    void allowedOriginsListIsHonouredAndNormalised() throws Exception {
        List<String> allowed = List.of("http://localhost:5173", "HTTPS://Front.Example:443");
        assertThat(status(allowed, "http://localhost:5173", null)).isEqualTo(200);
        assertThat(status(allowed, "https://front.example", null)).isEqualTo(200);
        assertThat(status(allowed, "http://localhost:5174", null)).isEqualTo(403);
    }

    @Test
    void neitherHeaderPasses() throws Exception {
        assertThat(status(List.of(), null, null)).isEqualTo(200);
    }
}
