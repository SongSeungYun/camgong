package com.camgong.auth;

import static com.camgong.support.AuthBodies.login;
import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.AuthBodies.verify;
import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.camgong.support.Cookies;
import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 21 */
// 로그 수준을 DEBUG 로 올려 디버그 줄에 새는 값까지 잡는다. 개발용 LoggingMailSender 가 링크를 찍는 줄만 제외한다.
@TestPropertySource(properties = "logging.level.com.camgong=DEBUG")
@ExtendWith(OutputCaptureExtension.class)
class PrivacyIT extends IntegrationTestBase {

    private static final Pattern FORBIDDEN_KEY = Pattern.compile("\"(email|password|passwordHash|emailKey)\"\\s*:");

    private final List<String> bodies = new ArrayList<>();

    private MvcResult call(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        bodies.add(result.getResponse().getContentAsString());
        return result;
    }

    private MockHttpServletRequestBuilder postJson(String url, Map<String, Object> body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    @Test
    void noResponseLogOrStoredValueLeaksEmailPasswordOrToken(CapturedOutput output) throws Exception {
        String email = "privacy.user@school.ac.kr";
        String password = "very-secret-pw1";

        // 가입 신청 응답은 신청자 본인이 보낸 이메일을 돌려주는 계약이라 따로 두고, 나머지 응답만 검사한다.
        mockMvc.perform(postJson("/api/auth/signup", signup(email, "프라이버시", password)));
        String link = mail.sent().get(0).verifyLink();
        String token = link.substring(link.indexOf("token=") + "token=".length());

        call(postJson("/api/auth/signup", signup(email, "다른이름", "other-secret-pw2")));    // 429
        call(postJson("/api/auth/signup", signup("m1@school.ac.kr")));                       // 409
        call(postJson("/api/auth/signup", signup("bad@gmail.com", "프라이버시", password)));  // 400
        call(postJson("/api/auth/verify", verify("wrong-token")));                           // 400
        MvcResult verified = call(postJson("/api/auth/verify", verify(token)));              // 200
        call(postJson("/api/auth/verify", verify(token)));                                   // 409
        call(postJson("/api/auth/login", login(email, "wrong-password")));                   // 401
        MvcResult loggedIn = call(postJson("/api/auth/login", login(email, password)));      // 200
        Cookie session = new Cookie("camgong_session", Cookies.sessionOf(loggedIn));
        call(get("/api/auth/me").cookie(session));
        call(get("/api/auth/me"));                                                           // 401
        call(postJson("/api/auth/signup/resend", Map.of("email", email)));                   // 404 (이미 인증됨)
        call(postJson("/api/auth/signup/resend", Map.of("email", "bad@gmail.com")));         // 404
        for (int i = 0; i < 5; i++) {
            call(postJson("/api/auth/login", login("locked@school.ac.kr", "wrong-password")));
        }
        call(postJson("/api/auth/login", login("locked@school.ac.kr", "wrong-password")));   // 429 LOGIN_LOCKED
        call(post("/api/auth/logout").cookie(session));                                       // 204

        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 3);
        call(get("/api/groupbuys/" + id).with(as(ORGANIZER)));
        call(get("/api/groupbuys/" + id).with(as(PARTICIPANT)));
        call(get("/api/groupbuys/" + id).with(as(OUTSIDER)));
        call(get("/api/groupbuys/" + id));                                                   // 401

        assertThat(bodies).isNotEmpty().allSatisfy(body -> {
            assertThat(FORBIDDEN_KEY.matcher(body).find()).as(body).isFalse();
            assertThat(body).doesNotContain("@school.ac.kr").doesNotContain("$2a$").doesNotContain(password)
                    .doesNotContain(token).doesNotContain("bad@gmail.com").doesNotContain("wrong-password")
                    .doesNotContain("privacy.user").doesNotContain("other-secret-pw2");
        });
        assertThat(verified.getResponse().getContentAsString()).contains("\"displayName\"");

        String logs = String.join("\n", output.getAll().lines().filter(l -> !l.contains("LoggingMailSender")).toList());
        assertThat(output.getAll()).contains("verification mail");   // 로그를 실제로 모으고 있다
        assertThat(logs).doesNotContain(token).doesNotContain(password).doesNotContain("other-secret-pw2")
                .doesNotContain(Cookies.sessionOf(loggedIn));

        String bcrypt = "^\\$2[aby]\\$\\d\\d\\$.{53}$";
        assertThat(jdbc.queryForObject("SELECT password_hash FROM member WHERE email_key = ?", String.class, email))
                .matches(bcrypt);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM pending_signup WHERE email_key = ?", String.class,
                email)).matches(bcrypt);
    }
}
