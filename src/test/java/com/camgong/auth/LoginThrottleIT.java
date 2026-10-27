package com.camgong.auth;

import static com.camgong.support.AuthBodies.login;
import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 16 */
class LoginThrottleIT extends IntegrationTestBase {

    private static final String EMAIL = "lock.user@school.ac.kr";

    @BeforeEach
    void member() {
        seedLoginMember(10, EMAIL, "잠금유저", "password1");
    }

    private ResultActions attempt(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(login(email, password))));
    }

    private void fail(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            attempt(email, "wrong-password").andExpect(status().isUnauthorized());
        }
    }

    private Integer failedCount(String key) {
        var rows = jdbc.queryForList("SELECT failed_count FROM login_attempt WHERE email_key = ?", Integer.class, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Test
    void fifthFailureLocksAndEvenCorrectCredentialsAreRefusedUntilFifteenMinutesPass() throws Exception {
        fail(EMAIL, 5);

        attempt(EMAIL, "password1").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("LOGIN_LOCKED"))
                .andExpect(jsonPath("$.retryAt").value("2026-10-27T09:15:00+09:00"))
                .andExpect(header().string("Retry-After", "900"));

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        attempt(EMAIL, "password1").andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofSeconds(1));
        attempt(EMAIL, "password1").andExpect(status().isOk());
        assertThat(failedCount(EMAIL)).isNull();
    }

    @Test
    void attemptsDuringTheLockNeitherCountNorExtendIt() throws Exception {
        fail(EMAIL, 5);
        clock.advance(Duration.ofMinutes(5));
        attempt(EMAIL, "wrong-password").andExpect(status().isTooManyRequests());
        attempt(EMAIL, "password1").andExpect(status().isTooManyRequests());

        assertThat(failedCount(EMAIL)).isEqualTo(5);
        clock.advance(Duration.ofMinutes(10));    // 처음 잠금 시각 + 15분
        attempt(EMAIL, "password1").andExpect(status().isOk());
    }

    @org.springframework.beans.factory.annotation.Autowired
    LoginService loginService;

    @Test
    void aFailureRacingIntoAnAlreadyLockedRowCountsButDoesNotMoveTheLockTime() throws Exception {
        fail(EMAIL, 5);
        clock.advance(Duration.ofMinutes(5));

        // 잠금 검사를 통과한 뒤 도착한 늦은 실패를 흉내 낸다(잠금 중 시도는 HTTP 로는 여기까지 오지 못한다).
        loginService.recordFailure("lock.user@school.ac.kr", clock.instant());

        assertThat(failedCount(EMAIL)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT locked_until FROM login_attempt WHERE email_key = ?",
                java.time.LocalDateTime.class, EMAIL)).isEqualTo(java.time.LocalDateTime.of(2026, 10, 27, 0, 15));
    }

    @Test
    void failuresAfterTheLockExpiredStartCountingFromOneAgain() throws Exception {
        fail(EMAIL, 5);
        clock.advance(Duration.ofMinutes(15));

        fail(EMAIL, 4);                                   // 새로 1..4: 아직 잠기지 않는다
        assertThat(failedCount(EMAIL)).isEqualTo(4);
        attempt(EMAIL, "wrong-password").andExpect(status().isUnauthorized());   // 5번째
        attempt(EMAIL, "password1").andExpect(status().isTooManyRequests());
    }

    @Test
    void successResetsTheCounterAndTheNextFailureCountsFromOne() throws Exception {
        fail(EMAIL, 4);
        attempt(EMAIL, "password1").andExpect(status().isOk());
        assertThat(failedCount(EMAIL)).isNull();

        fail(EMAIL, 1);
        assertThat(failedCount(EMAIL)).isEqualTo(1);
        fail(EMAIL, 3);
        attempt(EMAIL, "password1").andExpect(status().isOk());   // 4번 실패라 안 잠겼다
    }

    @Test
    void unknownEmailLocksAtTheFifthFailureWithTheSameShapeAsAnExistingAccount() throws Exception {
        String unknown = "nobody@school.ac.kr";
        fail(unknown, 5);
        fail(EMAIL, 5);

        MockHttpServletResponse a = attempt(unknown, "password1").andExpect(status().isTooManyRequests())
                .andReturn().getResponse();
        MockHttpServletResponse b = attempt(EMAIL, "password1").andExpect(status().isTooManyRequests())
                .andReturn().getResponse();
        assertThat(a.getContentAsString()).isEqualTo(b.getContentAsString());
        assertThat(a.getHeader("Retry-After")).isEqualTo(b.getHeader("Retry-After"));
    }

    @Test
    void overlongEmailIsRejectedAsInvalidInputInsteadOfFailingInTheCounterTable() throws Exception {
        attempt("a".repeat(250) + "@school.ac.kr", "x").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt", Integer.class)).isZero();
    }

    @Test
    void caseAndSpacesInTheEmailShareOneCounter() throws Exception {
        attempt("  LOCK.USER@school.ac.kr ", "x").andExpect(status().isUnauthorized());
        attempt("Lock.User@School.ac.kr", "x").andExpect(status().isUnauthorized());
        fail(EMAIL, 3);

        assertThat(failedCount(EMAIL)).isEqualTo(5);
        attempt(EMAIL, "password1").andExpect(status().isTooManyRequests());
    }

    @Test
    void concurrentFailuresAreCountedWithoutLossAndLockOnce() throws Exception {
        int n = 10;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> attempt(EMAIL, "wrong-password").andReturn().getResponse().getStatus()));
        }
        int unauthorized = 0;
        for (Future<Integer> f : results) {
            int s = f.get();
            assertThat(s).isIn(401, 429);
            if (s == 401) {
                unauthorized++;
            }
        }
        pool.shutdown();

        // 401 을 받은 요청은 모두 횟수에 들어갔고, 잠긴 뒤에 도착한 요청(429)은 세지 않았다.
        assertThat(unauthorized).isGreaterThanOrEqualTo(5);
        assertThat(failedCount(EMAIL)).isEqualTo(unauthorized);
        assertThat(jdbc.queryForObject("SELECT locked_until FROM login_attempt WHERE email_key = ?",
                java.time.LocalDateTime.class, EMAIL)).isEqualTo(java.time.LocalDateTime.of(2026, 10, 27, 0, 15));
        attempt(EMAIL, "password1").andExpect(status().isTooManyRequests());
    }
}
