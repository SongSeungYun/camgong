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
        mockMvc.perform(postJson("/api/auth/signup", signup(email)));
        String link = mail.sent().get(0).verifyLink();
        String token = link.substring(link.indexOf("token=") + "token=".length());

        call(postJson("/api/auth/signup", signup(email)));                                  // 429
        call(postJson("/api/auth/signup", signup("m1@school.ac.kr")));                       // 409
        call(postJson("/api/auth/signup", signup("bad@gmail.com")));                          // 400
        call(postJson("/api/auth/verify", verify("wrong-token", "프라이버시", "wrong-token-pw1")));    // 400 INVALID_TOKEN
        call(postJson("/api/auth/verify", verify(token, "가", "short-pw")));                    // 400 본문 오류(토큰 유지)
        call(postJson("/api/auth/verify", verify(token, "개설자", password)));                    // 409 DISPLAY_NAME_TAKEN
        MvcResult verified = call(postJson("/api/auth/verify", verify(token, "프라이버시", password)));  // 200
        call(postJson("/api/auth/verify", verify(token, "프라이버시", password)));                  // 409 TOKEN_USED
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
                    .doesNotContain("privacy.user").doesNotContain("wrong-token-pw1").doesNotContain("short-pw");
        });
        assertThat(verified.getResponse().getContentAsString()).contains("\"displayName\"");

        String logs = String.join("\n", output.getAll().lines().filter(l -> !l.contains("LoggingMailSender")).toList());
        assertThat(output.getAll()).contains("verification mail");   // 로그를 실제로 모으고 있다
        assertThat(logs).doesNotContain(token).doesNotContain(password).doesNotContain("wrong-token-pw1").doesNotContain("short-pw")
                .doesNotContain(Cookies.sessionOf(loggedIn));

        String bcrypt = "^\\$2[aby]\\$\\d\\d\\$.{53}$";
        assertThat(jdbc.queryForObject("SELECT password_hash FROM member WHERE email_key = ?", String.class, email))
                .matches(bcrypt);
    }

    /** 010 T016: 가입 경합의 UNIQUE 위반(MySQL 메시지 'Duplicate entry <값>')이 로그에 이메일·표시 이름을 남기지 않는다. */
    @Test
    void uniqueViolationsDuringSignupDoNotLogTheEmailOrDisplayName(CapturedOutput output) throws Exception {
        // 경합에서 먼저 끝난 가입을 직접 넣는다: 이름이 같은 회원, 이메일이 같은 회원.
        jdbc.update("INSERT INTO member (name, display_name_key, email, email_key, password_hash, consent_at, "
                + "consent_version, created_at, school) VALUES ('경합이름', '경합이름', 'winner.raced@school.ac.kr', "
                + "'winner.raced@school.ac.kr', 'x', NOW(), 'v1', NOW(), 'school.ac.kr')");

        mockMvc.perform(postJson("/api/auth/signup", signup("loser.raced@school.ac.kr")));
        String link = mail.sent().get(mail.sent().size() - 1).verifyLink();
        String nameToken = link.substring(link.indexOf("token=") + "token=".length());
        call(postJson("/api/auth/verify", verify(nameToken, "경합이름", "racing-pw-123")));      // 409 DISPLAY_NAME_TAKEN

        mockMvc.perform(postJson("/api/auth/signup", signup("dup.raced@school.ac.kr")));
        link = mail.sent().get(mail.sent().size() - 1).verifyLink();
        String emailToken = link.substring(link.indexOf("token=") + "token=".length());
        jdbc.update("UPDATE member SET email = 'dup.raced@school.ac.kr', email_key = 'dup.raced@school.ac.kr' "
                + "WHERE email_key = 'winner.raced@school.ac.kr'");
        call(postJson("/api/auth/verify", verify(emailToken, "다른이름", "racing-pw-123")));      // 409 ALREADY_REGISTERED

        assertThat(bodies).anySatisfy(b -> assertThat(b).contains("DISPLAY_NAME_TAKEN"))
                .anySatisfy(b -> assertThat(b).contains("ALREADY_REGISTERED"));
        String logs = String.join("\n", output.getAll().lines().filter(l -> !l.contains("LoggingMailSender")).toList());
        assertThat(logs).doesNotContain("경합이름").doesNotContain("raced@school.ac.kr").doesNotContain("Duplicate entry");
    }
}
