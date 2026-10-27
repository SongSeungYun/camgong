package com.camgong.auth;

import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.AuthBodies.verify;
import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 13, 14(나머지), 15, 20 */
class ResendIT extends IntegrationTestBase {

    private static final String EMAIL = "new@school.ac.kr";

    private ResultActions submit(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/signup/resend").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))));
    }

    private ResultActions verifyToken(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json(verify(token))));
    }

    private String lastToken() {
        String link = mail.sent().get(mail.sent().size() - 1).verifyLink();
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private int pendingRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pending_signup", Integer.class);
    }

    private int sentCount() {
        return jdbc.queryForObject("SELECT sent_count FROM pending_signup", Integer.class);
    }

    @Test
    void resendRightAfterSignupIsLimitedUntilOneMinutePasses() throws Exception {
        submit(signup(EMAIL)).andExpect(status().isAccepted());

        resend(EMAIL).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.retryAt").value("2026-10-27T09:01:00+09:00"))
                .andExpect(header().string("Retry-After", "60"));
        assertThat(mail.sent()).hasSize(1);

        clock.advance(Duration.ofSeconds(59));
        resend(EMAIL).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));

        clock.advance(Duration.ofSeconds(1));
        resend(EMAIL).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.resendAvailableAt").value("2026-10-27T09:02:00+09:00"));
        assertThat(mail.sent()).hasSize(2);
    }

    @Test
    void sixthMailInOneDayIsRejectedAndTheWindowResetsAfter24Hours() throws Exception {
        submit(signup(EMAIL)).andExpect(status().isAccepted());                         // 1
        for (int i = 2; i <= 5; i++) {
            clock.advance(Duration.ofMinutes(1));
            resend(EMAIL).andExpect(status().isAccepted());                             // 2..5
        }
        assertThat(sentCount()).isEqualTo(5);

        clock.advance(Duration.ofMinutes(1));
        // 창은 첫 신청(09:00)에서 시작했고 지금은 09:05 이다.
        resend(EMAIL).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAt").value("2026-10-28T09:00:00+09:00"));
        assertThat(mail.sent()).hasSize(5);

        clock.set(START.plus(Duration.ofHours(24)).minusSeconds(1));
        resend(EMAIL).andExpect(status().isTooManyRequests());

        clock.set(START.plus(Duration.ofHours(24)).plusSeconds(1));
        resend(EMAIL).andExpect(status().isAccepted());
        assertThat(sentCount()).isEqualTo(1);
        assertThat(mail.sent()).hasSize(6);
    }

    @Test
    void resubmitAlsoResetsTheWindowAfter24HoursAndReportsRetryAfterAtTheWindowEnd() throws Exception {
        submit(signup(EMAIL)).andExpect(status().isAccepted());
        for (int i = 2; i <= 5; i++) {
            clock.advance(Duration.ofMinutes(1));
            submit(signup(EMAIL)).andExpect(status().isAccepted());
        }
        submit(signup(EMAIL)).andExpect(status().isTooManyRequests());   // 같은 시각이라 1분 간격에 걸린다
        clock.advance(Duration.ofMinutes(1));
        submit(signup(EMAIL)).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", String.valueOf(24 * 3600 - 5 * 60)));

        clock.set(START.plus(Duration.ofHours(24)));
        submit(signup(EMAIL)).andExpect(status().isAccepted());
        assertThat(sentCount()).isEqualTo(1);
    }

    @Test
    void previousTokenStopsWorkingAfterResendAndTheNewOneWorks() throws Exception {
        submit(signup(EMAIL)).andExpect(status().isAccepted());
        String oldToken = lastToken();

        clock.advance(Duration.ofMinutes(2));
        resend(EMAIL).andExpect(status().isAccepted());
        String newToken = lastToken();

        assertThat(newToken).isNotEqualTo(oldToken);
        verifyToken(oldToken).andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("INVALID_TOKEN"));
        verifyToken(newToken).andExpect(status().isOk());
    }

    @Test
    void applyingAgainBeforeVerificationRefreshesTheTokenOnlyAndKeepsCounting() throws Exception {
        submit(signup(EMAIL)).andExpect(status().isAccepted());
        String oldToken = lastToken();

        clock.advance(Duration.ofMinutes(2));
        submit(signup(EMAIL)).andExpect(status().isAccepted());

        assertThat(pendingRows()).isEqualTo(1);
        assertThat(sentCount()).isEqualTo(2);
        assertThat(mail.sent()).hasSize(2);
        verifyToken(oldToken).andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("INVALID_TOKEN"));

        // 링크를 누른 사람이 정한 이름·비밀번호로 가입된다
        mockMvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json(com.camgong.support.AuthBodies.verify(lastToken(), "처음이름", "chosen-pw-1"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.member.displayName").value("처음이름"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(com.camgong.support.AuthBodies.login(EMAIL, "chosen-pw-1")))).andExpect(status().isOk());
    }

    @Test
    void resendIsCaseAndSpaceInsensitive() throws Exception {
        submit(signup(EMAIL)).andExpect(status().isAccepted());
        clock.advance(Duration.ofMinutes(2));

        resend("  NEW@School.ac.kr ").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value(EMAIL));        // 응답도 저장된 주소
        assertThat(pendingRows()).isEqualTo(1);
        // 메일은 요청에 적힌 대문자 섞인 문자열이 아니라 신청 때 저장된 주소로 나간다
        assertThat(mail.sent()).hasSize(2);
        assertThat(mail.sent().get(1).toEmail()).isEqualTo(EMAIL);
    }

    @Test
    void verifiedAndUnknownEmailsGetTheSame404() throws Exception {
        submit(signup(EMAIL)).andExpect(status().isAccepted());
        verifyToken(lastToken()).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(2));

        String verified = resend(EMAIL).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknown = resend("nobody@school.ac.kr").andExpect(status().isNotFound()).andReturn().getResponse()
                .getContentAsString();
        String seeded = resend("m1@school.ac.kr").andExpect(status().isNotFound()).andReturn().getResponse()
                .getContentAsString();

        assertThat(verified).isEqualTo(unknown).isEqualTo(seeded);
        assertThat(mail.sent()).hasSize(1);
    }

    @Test
    void nonAsciiAddressForResendIsRejected() throws Exception {
        submit(signup(EMAIL)).andExpect(status().isAccepted());
        clock.advance(Duration.ofMinutes(2));
        resend("\u212Aim@school.ac.kr").andExpect(status().isBadRequest());
        resend("new@school.ac.kr\u200b").andExpect(status().isBadRequest());
    }

    @Test
    void invalidEmailFormatIsRejected() throws Exception {
        resend("not-an-email").andExpect(status().isBadRequest());
    }

    @Test
    void concurrentIdenticalSignupsAcceptExactlyOne() throws Exception {
        Map<String, Object> body = signup(EMAIL);
        List<String> statuses = runConcurrently(8, () -> submit(body).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s.equals("202")).hasSize(1);
        assertThat(statuses).filteredOn(s -> !s.equals("202")).allMatch(s -> s.equals("429"));
        assertThat(pendingRows()).isEqualTo(1);
        assertThat(mail.sent()).hasSize(1);
    }

    @Test
    void concurrentResendsAcceptExactlyOne() throws Exception {
        submit(signup(EMAIL)).andExpect(status().isAccepted());
        clock.advance(Duration.ofMinutes(2));

        List<String> statuses = runConcurrently(8, () -> resend(EMAIL).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s.equals("202")).hasSize(1);
        assertThat(statuses).filteredOn(s -> !s.equals("202")).allMatch(s -> s.equals("429"));
        assertThat(sentCount()).isEqualTo(2);
        assertThat(mail.sent()).hasSize(2);
    }

    private List<String> runConcurrently(int n, Callable<Integer> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(call));
        }
        List<String> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(String.valueOf(f.get()));
        }
        pool.shutdown();
        return statuses;
    }
}
