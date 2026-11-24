package com.camgong.auth;

import static com.camgong.support.GroupBuyBodies.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.IntegrationTestBase;

import jakarta.servlet.http.Cookie;

/** 011 비밀번호 재설정 T002~T008(T004 는 PasswordResetMailTest). */
class PasswordResetIT extends IntegrationTestBase {

    private static final String MEMBER = "member@school.ac.kr";
    private static final String PENDING = "pending@school.ac.kr";
    private static final String GONE = "gone@school.ac.kr";
    private static final String NOBODY = "nobody@school.ac.kr";
    private static final List<String> FOUR = List.of(MEMBER, PENDING, GONE, NOBODY);

    @Autowired
    SessionCleanup cleanup;

    @Autowired
    WithdrawLedgerReplay replay;

    private ResultActions request(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))));
    }

    private ResultActions confirm(String token, String password) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("token", token);
        body.put("password", password);
        return mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    private List<LoggingMailSender.Sent> resetMails() {
        return mail.sent().stream().filter(s -> s.verifyLink().contains("/password-reset/confirm?token=")).toList();
    }

    private String lastToken() {
        List<LoggingMailSender.Sent> sent = resetMails();
        String link = sent.get(sent.size() - 1).verifyLink();
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private String requestToken() throws Exception {
        request(MEMBER).andExpect(status().isAccepted());
        return lastToken();
    }

    private LocalDateTime at(Duration fromNow) {
        return LocalDateTime.ofInstant(clock.instant().plus(fromNow), ZoneOffset.UTC);
    }

    /** 가입한 회원, 인증 전 신청, 탈퇴한 회원, 처음 보는 주소. */
    private void seedFourAddresses() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", PENDING)))).andExpect(status().isAccepted());
        seedLoginMember(11, GONE, "떠난이", "password1");
        mockMvc.perform(post("/api/auth/withdraw").with(as(11)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password1\"}")).andExpect(status().isNoContent());
        mail.clear();
    }

    // ---- T002 ----

    @Test
    void everyKindOfAddressGetsTheSameAnswerAndOnlyTheMemberGetsMail() throws Exception {
        seedFourAddresses();
        List<String> bodies = new ArrayList<>();
        for (String email : FOUR) {
            bodies.add(request(email).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        }
        assertThat(bodies).containsOnly("{\"retryAt\":\"2026-10-27T09:01:00+09:00\"}");
        assertThat(mail.sent()).singleElement().satisfies(s -> {
            assertThat(s.toEmail()).isEqualTo(MEMBER);
            assertThat(s.verifyLink()).startsWith("http://localhost:5173/password-reset/confirm?token=");
        });
        assertThat(jdbc.queryForList("SELECT token_hash IS NOT NULL FROM password_reset ORDER BY email_key = ?",
                Boolean.class, MEMBER)).containsExactly(false, false, false, true);
    }

    @Test
    void aNonUniversityAddressIsRejectedLikeSignup() throws Exception {
        request("someone@gmail.com").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("대학 메일로만 가입할 수 있습니다."));
        request("not-an-email").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("email"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset", Integer.class)).isZero();
    }

    // ---- T003 ----

    @Test
    void theLimitsAreTheSameForEveryKindOfAddress() throws Exception {
        seedFourAddresses();
        for (String email : FOUR) {
            request(email).andExpect(status().isAccepted());
            request(email).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.title").value("TOO_MANY_REQUESTS"))
                    .andExpect(jsonPath("$.retryAt").value("2026-10-27T09:01:00+09:00"))
                    .andExpect(header().string("Retry-After", "60"));
        }
        for (int i = 2; i <= 5; i++) {
            clock.advance(Duration.ofMinutes(1));
            for (String email : FOUR) {
                request(email).andExpect(status().isAccepted());
            }
        }
        clock.advance(Duration.ofMinutes(1));
        for (String email : FOUR) {
            request(email).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.retryAt").value("2026-10-28T09:00:00+09:00"));
        }
        clock.advance(Duration.ofHours(24));
        for (String email : FOUR) {
            request(email).andExpect(status().isAccepted());
        }
        assertThat(resetMails()).hasSize(6).allSatisfy(s -> assertThat(s.toEmail()).isEqualTo(MEMBER));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt", Integer.class))
                .as("재설정 요청은 로그인 실패로 세지 않는다").isZero();
    }

    /** FR-009: 로그인이 잠긴 회원도 요청할 수 있고, 요청은 잠금 기록을 건드리지 않는다(관제탑 검수 T016). */
    @Test
    void aLockedMemberCanStillRequestAndTheLockIsUntouched() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        LocalDateTime until = at(Duration.ofMinutes(15));
        jdbc.update("INSERT INTO login_attempt (email_key, failed_count, locked_until, updated_at) VALUES (?, 5, ?, ?)",
                MEMBER, until, at(Duration.ZERO));

        request(MEMBER).andExpect(status().isAccepted());

        assertThat(resetMails()).hasSize(1);
        assertThat(jdbc.queryForMap("SELECT failed_count, locked_until, updated_at FROM login_attempt WHERE email_key = ?",
                MEMBER)).containsEntry("failed_count", 5).containsEntry("locked_until", until)
                .containsEntry("updated_at", at(Duration.ZERO));
    }

    // ---- T005 ----

    @Test
    void confirmingChangesThePasswordUnlocksLoginAndEndsEverySession() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        jdbc.update("INSERT INTO login_attempt (email_key, failed_count, locked_until, updated_at) VALUES (?, 5, ?, ?)",
                MEMBER, at(Duration.ofMinutes(15)), at(Duration.ZERO));
        mockMvc.perform(get("/api/auth/me").with(as(10))).andExpect(status().isOk());
        String token = requestToken();
        clock.advance(Duration.ofSeconds(1));

        MockHttpServletResponse response = confirm(token, "new-password1").andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(response.getHeaders("Set-Cookie")).as("쿠키는 건드리지 않는다").isEmpty();
        assertThat(encoder.matches("new-password1",
                jdbc.queryForObject("SELECT password_hash FROM member WHERE id = 10", String.class))).isTrue();
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM password_reset", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_session", Integer.class)).isZero();
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("camgong_session", "test-session-10")))
                .andExpect(status().isUnauthorized());
        login(MEMBER, "password1").andExpect(status().isUnauthorized());
        login(MEMBER, "new-password1").andExpect(status().isOk());
    }

    // ---- T006 ----

    @Test
    void badTokensAreRejected() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        confirm(null, "new-password1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("INVALID_TOKEN"));
        confirm("no-such-token", "new-password1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TOKEN"));

        String first = requestToken();
        clock.advance(Duration.ofMinutes(1));
        String second = requestToken();
        confirm(first, "new-password1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("INVALID_TOKEN"));

        confirm(second, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        confirm(second, " ".repeat(10)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        confirm(second, "가".repeat(25)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        confirm(second, "new-password1").andExpect(status().isNoContent());   // 비밀번호 오류는 토큰을 쓰지 않는다
        confirm(second, "new-password2").andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("TOKEN_USED"));
    }

    @Test
    void aTokenExpiresAfterOneHour() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        String token = requestToken();
        clock.advance(Duration.ofHours(1));
        confirm(token, "new-password1").andExpect(status().isGone()).andExpect(jsonPath("$.title").value("TOKEN_EXPIRED"));
    }

    @Test
    void aTokenOfAMemberWhoWithdrewIsInvalid() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        String token = requestToken();
        mockMvc.perform(post("/api/auth/withdraw").with(as(10)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password1\"}")).andExpect(status().isNoContent());
        confirm(token, "new-password1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("INVALID_TOKEN"));
    }

    /**
     * 같은 링크 두 건이 둘 다 사전 검사를 통과한 뒤 회원 잠금에서 겹치게 한다(관제탑 검수 T017). 그냥 동시에 보내면 대부분 하나가 먼저 끝나
     * 사전 검사에서 409 가 나와, 트랜잭션 안의 토큰 재확인을 빼도 통과한다.
     */
    @Test
    void theSameLinkSubmittedTwiceWhileBothWaitOnTheMemberSucceedsOnce() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        String token = requestToken();
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT id FROM member WHERE id = 10 FOR UPDATE", Long.class);
                locked.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            locked.await();
            Future<Integer> first = pool.submit(() -> confirm(token, "new-password1").andReturn().getResponse().getStatus());
            Future<Integer> second = pool.submit(() -> confirm(token, "new-password2").andReturn().getResponse().getStatus());
            awaitLockWaits(2, 5000);
            go.countDown();
            holder.get();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(204, 409);
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }

    // ---- T006a ----

    @Test
    void aSessionFromBeforeTheResetIsRefusedEvenIfTheDeleteMissedIt() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        String token = requestToken();
        LocalDateTime before = at(Duration.ZERO);
        clock.advance(Duration.ofSeconds(1));
        confirm(token, "new-password1").andExpect(status().isNoContent());

        // 커밋 뒤 삭제가 놓친 세션(삭제 실패, 경쟁)
        jdbc.update("INSERT INTO login_session (token_hash, member_id, created_at, last_used_at) VALUES (?, 10, ?, ?)",
                Tokens.sha256Hex("missed"), before, at(Duration.ZERO));
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("camgong_session", "missed")))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_session", Integer.class)).as("거부하며 지운다").isZero();

        String fresh = login(MEMBER, "new-password1").andExpect(status().isOk()).andReturn().getResponse()
                .getCookie("camgong_session").getValue();
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("camgong_session", fresh))).andExpect(status().isOk());
    }

    /**
     * 관제탑 검수 T015: 커밋 뒤 세션 삭제가 잠금을 기다리는 사이 새 비밀번호로 로그인하면, 그 세션은 삭제에 휩쓸리지 않는다.
     * 다른 트랜잭션이 옛 세션 행을 잡아 삭제를 세워 두고, 그 사이 로그인한다(새 세션은 색인에서 옛 세션 뒤라 아직 잠기지 않았다).
     */
    @Test
    void aSessionMadeWithTheNewPasswordSurvivesTheDeleteAfterCommit() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        mockMvc.perform(get("/api/auth/me").with(as(10))).andExpect(status().isOk());
        String token = requestToken();
        long oldSession = jdbc.queryForObject("SELECT id FROM login_session WHERE member_id = 10", Long.class);
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.update("UPDATE login_session SET last_used_at = last_used_at WHERE id = ?", oldSession);   // PK: 그 행만
                locked.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            locked.await();
            Future<Integer> confirmed = pool.submit(() -> confirm(token, "new-password1").andReturn().getResponse().getStatus());
            awaitLockWait(5000);                                  // 커밋은 끝났고 세션 삭제가 옛 세션 행에서 막혔다
            clock.advance(Duration.ofSeconds(1));
            String fresh = login(MEMBER, "new-password1").andExpect(status().isOk()).andReturn().getResponse()
                    .getCookie("camgong_session").getValue();
            go.countDown();
            holder.get();
            assertThat(confirmed.get()).isEqualTo(204);
            mockMvc.perform(get("/api/auth/me").cookie(new Cookie("camgong_session", fresh))).andExpect(status().isOk());
            mockMvc.perform(get("/api/auth/me").cookie(new Cookie("camgong_session", "test-session-10")))
                    .andExpect(status().isUnauthorized());
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }

    /**
     * 옛 비밀번호 로그인과 재설정 확인이 겹쳐도 재설정 뒤에 쓸 수 있는 옛 비밀번호 세션은 없다. 시계가 멈춰 있어 세션 생성 시각이 변경 시각과 같으므로
     * password_changed_at 비교로는 막히지 않는다: 로그인의 조건부 세션 INSERT 만 이 경쟁을 막는다.
     */
    @Test
    void anOldPasswordLoginRacingTheResetNeverLeavesAUsableSession() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        String oldHash = jdbc.queryForObject("SELECT password_hash FROM member WHERE id = 10", String.class);
        int usable = 0;
        int loggedIn = 0;
        for (int i = 0; i < 100; i++) {
            jdbc.update("UPDATE member SET password_hash = ?, password_changed_at = NULL WHERE id = 10", oldHash);
            jdbc.update("DELETE FROM login_session");
            jdbc.update("DELETE FROM login_attempt");
            jdbc.update("DELETE FROM password_reset");
            String token = "race-" + i;
            jdbc.update("INSERT INTO password_reset (email_key, member_id, token_hash, token_expires_at, last_sent_at, "
                    + "window_started_at, sent_count, created_at) VALUES (?, 10, ?, ?, ?, ?, 1, ?)",
                    MEMBER, Tokens.sha256Hex(token), at(Duration.ofHours(1)), at(Duration.ZERO), at(Duration.ZERO),
                    at(Duration.ZERO));
            List<MockHttpServletResponse> responses = race(
                    () -> login(MEMBER, "password1").andReturn().getResponse(),
                    () -> confirm(token, "new-password1").andReturn().getResponse());
            assertThat(responses.get(1).getStatus()).isEqualTo(204);
            assertThat(responses.get(0).getStatus()).isIn(200, 401);
            if (responses.get(0).getStatus() == 200) {
                loggedIn++;
                String raw = responses.get(0).getCookie("camgong_session").getValue();
                if (mockMvc.perform(get("/api/auth/me").cookie(new Cookie("camgong_session", raw)))
                        .andReturn().getResponse().getStatus() == 200) {
                    usable++;
                }
            }
        }
        assertThat(usable).as("재설정 뒤 쓸 수 있는 옛 비밀번호 세션 (옛 비밀번호 로그인 성공 %d회)", loggedIn).isZero();
    }

    // ---- T007 ----

    @Test
    void withdrawingDeletesTheResetRecord() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        requestToken();
        mockMvc.perform(post("/api/auth/withdraw").with(as(10)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password1\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset", Integer.class)).isZero();
    }

    @Test
    void replayingTheLedgerDeletesTheResetRecordToo() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        requestToken();
        java.nio.file.Files.writeString(LEDGER, "10\t" + LocalDateTime.ofInstant(START, ZoneOffset.UTC) + "\t"
                + at(Duration.ZERO) + "\n");
        assertThat(replay.replay()).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset", Integer.class)).isZero();
    }

    @Test
    void confirmingAndWithdrawingAtOnceNeitherDeadlocksNorFails() throws Exception {
        for (int i = 0; i < 20; i++) {
            long id = 30 + i;
            String email = "race" + i + "@school.ac.kr";
            seedLoginMember(id, email, "경쟁" + i, "password1");
            request(email).andExpect(status().isAccepted());
            String token = lastToken();
            List<Integer> statuses = race(
                    () -> confirm(token, "new-password1").andReturn().getResponse().getStatus(),
                    () -> mockMvc.perform(post("/api/auth/withdraw").with(as(id)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"password1\"}")).andReturn().getResponse().getStatus());
            // 재설정이 먼저면 탈퇴는 옛 비밀번호라 400 이거나, 세션이 지워져 401 이다. 탈퇴가 먼저면 재설정은 토큰이 없어 400 이다.
            assertThat(statuses.get(0)).isIn(204, 400);
            assertThat(statuses.get(1)).isIn(204, 400, 401);
            boolean withdrawn = jdbc.queryForObject("SELECT withdrawn_at IS NOT NULL FROM member WHERE id = ?",
                    Boolean.class, id);
            assertThat(withdrawn).isEqualTo(statuses.get(1) == 204);
            if (withdrawn) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset WHERE member_id = ?", Integer.class, id))
                        .isZero();
            }
        }
    }

    /** 리뷰: 요청은 재설정 행을 먼저 잠그고 FK 확인으로 회원 행을 기다렸다(탈퇴와 반대 순서). 교착하면 회원 주소만 500 이 된다. */
    @Test
    void requestingAndWithdrawingAtOnceNeitherDeadlocksNorFails() throws Exception {
        for (int i = 0; i < 30; i++) {
            long id = 60 + i;
            String email = "rw" + i + "@school.ac.kr";
            seedLoginMember(id, email, "요청탈퇴" + i, "password1");
            List<Integer> statuses = race(
                    () -> request(email).andReturn().getResponse().getStatus(),
                    () -> mockMvc.perform(post("/api/auth/withdraw").with(as(id)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"password1\"}")).andReturn().getResponse().getStatus());
            assertThat(statuses).containsExactly(202, 204);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset WHERE member_id = ?", Integer.class, id))
                    .as("탈퇴한 회원을 가리키는 재설정 줄이 남지 않는다").isZero();
        }
    }

    /** 위 경쟁을 운 없이 재현한다: 탈퇴 트랜잭션이 회원 행을 쥔 채 요청이 들어오고, 그 뒤 탈퇴가 재설정 행을 지운다(erase 순서). */
    @Test
    void aRequestArrivingWhileAWithdrawalHoldsTheMemberWaitsInsteadOfDeadlocking() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> withdraw = pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT id FROM member WHERE id = 10 FOR UPDATE", Long.class);
                locked.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                jdbc.update("DELETE FROM password_reset WHERE email_key = ?", MEMBER);
            }));
            locked.await();
            Future<Integer> request = pool.submit(() -> request(MEMBER).andReturn().getResponse().getStatus());
            awaitLockWait(5000);
            go.countDown();
            withdraw.get();
            assertThat(request.get()).isEqualTo(202);
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void twoRequestsForTheSameAddressAtOnceCountOnce() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofDays(1));
            String email = i % 2 == 0 ? MEMBER : "nobody" + i + "@school.ac.kr";
            List<Integer> statuses = race(() -> request(email).andReturn().getResponse().getStatus(),
                    () -> request(email).andReturn().getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(202, 429);
        }
    }

    /**
     * 로그인의 조건부 세션 INSERT 를 경쟁 운 없이 본다: 재설정이 회원 행을 잠근 동안 들어온 INSERT 는 그 커밋을 기다리고, 해시가 바뀌었으면
     * 아무것도 넣지 않는다(위 경쟁 테스트는 커밋 뒤 삭제가 대부분을 지워 이 경로를 드물게만 탄다).
     */
    @Autowired
    SessionService sessionService;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;

    @Test
    void aSessionInsertWaitingOnAResetInsertsNothingOnceTheHashChanged() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        String oldHash = jdbc.queryForObject("SELECT password_hash FROM member WHERE id = 10", String.class);
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> reset = pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT id FROM member WHERE id = 10 FOR UPDATE", Long.class);
                locked.countDown();
                try {
                    done.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                jdbc.update("UPDATE member SET password_hash = 'changed' WHERE id = 10");
            }));
            locked.await();
            Future<java.util.Optional<String>> login = Executors.newSingleThreadExecutor()
                    .submit(() -> sessionService.issueIfPassword(10, oldHash));
            awaitLockWait(5000);
            done.countDown();
            reset.get();
            assertThat(login.get()).isEmpty();
        } finally {
            done.countDown();
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_session", Integer.class)).isZero();
        assertThat(sessionService.issueIfPassword(10, "changed")).isPresent();
    }

    // ---- T008 ----

    /** 마지막 요청부터 1일(결정 7): 그때는 창·1분 간격·토큰 1시간이 모두 지나 없는 줄과 같다. 토큰 상태와 상관없이 그 전에는 남긴다. */
    @Test
    void cleanupRemovesRecordsWhoseLastRequestIsADayOld() {
        Duration day = Duration.ofHours(24).plusSeconds(1);
        Duration windowOld = Duration.ofHours(25);
        row("old-no-token@school.ac.kr", windowOld, day, null, null);
        row("old-used@school.ac.kr", windowOld, day, Duration.ofHours(-23), Duration.ofHours(-24));
        row("no-token@school.ac.kr", windowOld, Duration.ofSeconds(30), null, null);
        row("used@school.ac.kr", windowOld, Duration.ofSeconds(30), Duration.ofMinutes(59), Duration.ofSeconds(-10));
        row("live@school.ac.kr", windowOld, Duration.ofSeconds(30), Duration.ofMinutes(59), null);

        cleanup.run();

        assertThat(jdbc.queryForList("SELECT email_key FROM password_reset ORDER BY email_key", String.class))
                .containsExactly("live@school.ac.kr", "no-token@school.ac.kr", "used@school.ac.kr");
    }

    /** 관제탑 검수 T014: 창이 막 지난 직후의 정리가 비회원 줄만 지우면 다음 요청이 비회원 202·회원 429 로 갈려 가입이 드러난다. */
    @Test
    void cleanupRightAfterTheWindowEndsKeepsMemberAndNonMemberAlike() throws Exception {
        seedLoginMember(10, MEMBER, "회원", "password1");
        request(MEMBER).andExpect(status().isAccepted());
        request(NOBODY).andExpect(status().isAccepted());
        clock.advance(Duration.ofHours(24).minusSeconds(30));
        request(MEMBER).andExpect(status().isAccepted());
        request(NOBODY).andExpect(status().isAccepted());
        clock.advance(Duration.ofSeconds(40));

        cleanup.run();

        clock.advance(Duration.ofSeconds(10));
        request(MEMBER).andExpect(status().isTooManyRequests());
        request(NOBODY).andExpect(status().isTooManyRequests());
        assertThat(resetMails()).hasSize(2);
    }

    private void row(String key, Duration windowAgo, Duration lastSentAgo, Duration expiresIn, Duration usedIn) {
        jdbc.update("INSERT INTO password_reset (email_key, member_id, token_hash, token_expires_at, used_at, last_sent_at, "
                + "window_started_at, sent_count, created_at) VALUES (?, NULL, ?, ?, ?, ?, ?, 2, ?)",
                key, expiresIn == null ? null : Tokens.sha256Hex(key), expiresIn == null ? null : at(expiresIn),
                usedIn == null ? null : at(usedIn), at(lastSentAgo.negated()), at(windowAgo.negated()),
                at(windowAgo.negated()));
    }

    /** 두 작업을 최대한 같이 출발시키고 결과를 순서대로 돌려준다. */
    @SafeVarargs
    private static <T> List<T> race(Callable<T>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> out = new ArrayList<>();
            for (Future<T> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
