package com.camgong.auth;

import static com.camgong.support.AuthBodies.login;
import static com.camgong.support.AuthBodies.signup;
import static com.camgong.support.AuthBodies.verify;
import static com.camgong.support.GroupBuyBodies.json;
import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.support.Concurrent;
import com.camgong.support.IntegrationTestBase;

import jakarta.servlet.http.Cookie;

/** 010 회원 탈퇴(T002~T005, T007, T004a 일부). 탈퇴하는 회원은 20(w@school.ac.kr, 비밀번호 password1)이다. */
class WithdrawIT extends IntegrationTestBase {

    private static final long ME = 20L;
    private static final String EMAIL = "w@school.ac.kr";
    private static final String PASSWORD = "password1";
    private static final String WITHDRAWN = "탈퇴한 회원";

    @BeforeEach
    void me() {
        seedLoginMember(ME, EMAIL, "탈퇴자", PASSWORD);
    }

    private ResultActions withdraw(long member, String body) throws Exception {
        return mockMvc.perform(post("/api/auth/withdraw").with(as(member))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions withdraw(String password) throws Exception {
        return withdraw(ME, json(Map.of("password", password)));
    }

    private LocalDateTime utc(java.time.Instant t) {
        return LocalDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    private void participate(long groupBuyId, long memberId, int quantity) {
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, ?, ?)",
                groupBuyId, memberId, quantity, utc(START));
    }

    private void notifyMember(long recipient, String eventId) {
        jdbc.update("INSERT INTO notification (event_id, recipient_id, type, group_buy_id, group_buy_title, created_at) "
                + "VALUES (?, ?, 'GROUPBUY_COMPLETED', 1, 't', ?)", eventId, recipient, utc(START));
    }

    private List<Map<String, Object>> records() {
        return jdbc.queryForList("SELECT g.id, g.status, g.secured_quantity, g.organizer_id, p.member_id, p.quantity, "
                + "p.paid_at, p.received_at FROM group_buy g LEFT JOIN participation p ON p.group_buy_id = g.id ORDER BY g.id, p.id");
    }

    // --- T002 ---

    @Test
    void withdrawingAnonymisesTheMemberEndsEverySessionAndKeepsTheRecords() throws Exception {
        long done = groupBuy().status("COMPLETED").quantities(24, 24).insert(jdbc);
        participate(done, ORGANIZER, 9);
        participate(done, ME, 6);
        participate(done, PARTICIPANT, 9);
        jdbc.update("UPDATE participation SET paid_at = ? WHERE member_id = ?", utc(START), ME);
        notifyMember(ME, "e1");
        notifyMember(PARTICIPANT, "e1");
        jdbc.update("INSERT INTO pending_signup (email_key, email, token_hash, token_expires_at, last_sent_at, "
                + "window_started_at, sent_count, created_at) VALUES (?, ?, REPEAT('a', 64), ?, ?, ?, 1, ?)",
                EMAIL, EMAIL, utc(START.plusSeconds(3600)), utc(START), utc(START), utc(START));
        jdbc.update("INSERT INTO login_attempt (email_key, failed_count, updated_at) VALUES (?, 1, ?)", EMAIL, utc(START));
        jdbc.update("INSERT INTO login_session (token_hash, member_id, created_at, last_used_at) "
                + "VALUES (SHA2('other-device', 256), ?, ?, ?)", ME, utc(START), utc(START));
        List<Map<String, Object>> before = records();

        withdraw(PASSWORD).andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("camgong_session=;")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        Map<String, Object> m = jdbc.queryForMap("SELECT * FROM member WHERE id = ?", ME);
        assertThat(m.get("name")).isEqualTo(WITHDRAWN);
        assertThat(m.get("display_name_key")).isNull();
        assertThat(m.get("email")).isNull();
        assertThat(m.get("email_key")).isNull();
        assertThat(encoder.matches(PASSWORD, (String) m.get("password_hash"))).isFalse();
        assertThat(m.get("withdrawn_at")).isEqualTo(utc(START));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_session WHERE member_id = ?", Integer.class, ME)).isZero();
        assertThat(jdbc.queryForList("SELECT recipient_id FROM notification", Long.class)).containsExactly(PARTICIPANT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_signup", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt", Integer.class)).isZero();
        assertThat(records()).isEqualTo(before);

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("camgong_session", "other-device")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionIssuedAfterWithdrawalIsStillRejected() throws Exception {
        withdraw(PASSWORD).andExpect(status().isNoContent());
        // 커밋 뒤 세션 삭제가 실패해 남은 세션, 또는 삭제 뒤에 들어온 세션(동시 로그인)도 탈퇴한 회원이라 받지 않는다
        jdbc.update("INSERT INTO login_session (token_hash, member_id, created_at, last_used_at) "
                + "VALUES (SHA2('left-over', 256), ?, ?, ?)", ME, utc(START), utc(START));
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("camgong_session", "left-over")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications").cookie(new Cookie("camgong_session", "left-over")))
                .andExpect(status().isUnauthorized());
        // 로그인이 회원을 읽은 뒤 탈퇴가 커밋되고 그 다음 세션이 들어간 경쟁: as() 는 세션 행을 새로 만든다
        mockMvc.perform(get("/api/auth/me").with(as(ME))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groupbuys/1").with(as(ME))).andExpect(status().isUnauthorized());
        withdraw(PASSWORD).andExpect(status().isUnauthorized());
    }

    // --- T003 ---

    @Test
    void theOldEmailAndPasswordLookLikeAnUnknownAccountAndBothCanBeUsedAgain() throws Exception {
        withdraw(PASSWORD).andExpect(status().isNoContent());

        String withdrawn = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(login(EMAIL, PASSWORD)))).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(login("nobody@school.ac.kr", PASSWORD)))).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(withdrawn).isEqualTo(unknown);

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(signup(EMAIL))))
                .andExpect(status().isAccepted());
        String link = mail.sent().get(mail.sent().size() - 1).verifyLink();
        mockMvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json(verify(link.substring(link.indexOf("token=") + 6), "탈퇴자", "new-password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.displayName").value("탈퇴자"));
        assertThat(jdbc.queryForObject("SELECT id FROM member WHERE email_key = ?", Long.class, EMAIL)).isNotEqualTo(ME);
    }

    // --- T004 ---

    @Test
    void aWrongPasswordChangesNothingButCountsAsALoginFailure() throws Exception {
        withdraw("wrong-password").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("비밀번호가 맞지 않습니다."));

        assertThat(jdbc.queryForObject("SELECT name FROM member WHERE id = ?", String.class, ME)).isEqualTo("탈퇴자");
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM member WHERE id = ?", Boolean.class, ME)).isTrue();
        assertThat(jdbc.queryForObject("SELECT failed_count FROM login_attempt WHERE email_key = ?", Integer.class, EMAIL))
                .isEqualTo(1);
        mockMvc.perform(get("/api/auth/me").with(as(ME))).andExpect(status().isOk());
    }

    @Test
    void fiveWrongPasswordsLockBothWithdrawAndLogin() throws Exception {
        for (int i = 0; i < 5; i++) {
            withdraw("wrong-password").andExpect(status().isBadRequest());
        }
        withdraw(PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("LOGIN_LOCKED"))
                .andExpect(jsonPath("$.retryAt").exists())
                .andExpect(header().exists("Retry-After"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(login(EMAIL, PASSWORD))))
                .andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM member WHERE id = ?", Boolean.class, ME)).isTrue();

        clock.advance(Duration.ofMinutes(16));
        withdraw(PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void theRightPasswordAfterSomeFailuresWithdrawsAndClearsTheCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            withdraw("wrong-password").andExpect(status().isBadRequest());
        }
        withdraw(PASSWORD).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt", Integer.class)).isZero();
    }

    @Test
    void missingPasswordAnonymousAndForeignOrigin() throws Exception {
        withdraw(ME, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        withdraw("").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("password"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt", Integer.class)).isZero();

        mockMvc.perform(post("/api/auth/withdraw").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.title").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/auth/withdraw").with(as(ME)).header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM member WHERE id = ?", Boolean.class, ME)).isTrue();
    }

    // --- T004a ---

    @Test
    void twoWithdrawalsAtOnceOnlyOneSucceedsAndNoneIs500() throws Exception {
        Callable<Integer> call = () -> withdraw(PASSWORD).andReturn().getResponse().getStatus();
        List<Integer> statuses = Concurrent.all(List.of(call, call));
        assertThat(statuses).containsExactlyInAnyOrder(204, 401);
    }

    @Test
    void theReservedNameCannotBeTakenAtSignup() throws Exception {
        for (String name : List.of(WITHDRAWN, "  탈퇴한 회원\u3000", "탈퇴한회원", "탈퇴한  회원", "탈퇴한\u3000회원",
                "탈\u00A0퇴한 회 원")) {
            mail.clear();
            jdbc.update("DELETE FROM pending_signup");
            mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                    .content(json(signup("new@school.ac.kr")))).andExpect(status().isAccepted());
            String link = mail.sent().get(0).verifyLink();
            mockMvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                            .content(json(verify(link.substring(link.indexOf("token=") + 6), name))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("displayName"))
                    .andExpect(jsonPath("$.errors[0].message").value("쓸 수 없는 이름입니다."));
        }
    }

    // --- T005 ---

    @Test
    void othersSeeTheWithdrawnMemberAsWithdrawnAndTheOrganizerKeepsChecking() throws Exception {
        long joined = groupBuy().status("COMPLETED").quantities(24, 24).price(1200).insert(jdbc);
        participate(joined, ORGANIZER, 9);
        participate(joined, ME, 6);
        participate(joined, PARTICIPANT, 9);
        long organized = groupBuy().organizer(ME).status("FAILED").quantities(24, 12).insert(jdbc);
        participate(organized, ME, 9);
        participate(organized, PARTICIPANT, 3);

        withdraw(PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groupbuys/" + joined + "/check-sheet").with(as(ORGANIZER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.memberId == 20)].name").value(WITHDRAWN))
                .andExpect(jsonPath("$.rows[?(@.memberId == 20)].quantity").value(6))
                .andExpect(jsonPath("$.rows[?(@.memberId == 20)].amount").value(7200));
        mockMvc.perform(put("/api/groupbuys/" + joined + "/participations/" + ME + "/payment").with(as(ORGANIZER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isOk());
        mockMvc.perform(put("/api/groupbuys/" + joined + "/participations/" + ME + "/receipt").with(as(ORGANIZER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/groupbuys/" + joined).with(as(ORGANIZER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[?(@.memberId == 20)].name").value(WITHDRAWN));
        mockMvc.perform(get("/api/groupbuys/" + organized).with(as(PARTICIPANT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.organizerName").value(WITHDRAWN));
    }

    // --- T007 ---

    @Test
    void activeGroupBuysBlockWithdrawalAndAreListedInIdOrder() throws Exception {
        long openOwn = groupBuy().organizer(ME).title("내가 연 공구").insert(jdbc);
        participate(openOwn, ME, 9);
        long openJoined = groupBuy().title("참여한 공구").insert(jdbc);
        participate(openJoined, ORGANIZER, 9);
        participate(openJoined, ME, 1);
        long pending = groupBuy().title("확정 대기").deadline(START.minusSeconds(60)).insert(jdbc);
        participate(pending, ORGANIZER, 9);
        participate(pending, ME, 1);
        long done = groupBuy().status("COMPLETED").insert(jdbc);
        participate(done, ME, 1);
        long otherOpen = groupBuy().title("남의 공구").insert(jdbc);   // 참여하지 않은 모집 중 공구는 막지 않는다
        participate(otherOpen, ORGANIZER, 9);
        jdbc.update("INSERT INTO login_attempt (email_key, failed_count, updated_at) VALUES (?, 2, ?)", EMAIL, utc(START));
        List<Map<String, Object>> before = records();

        withdraw(PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ACTIVE_GROUPBUY"))
                .andExpect(jsonPath("$.detail").value("진행 중인 공구가 있어 탈퇴할 수 없습니다."))
                .andExpect(jsonPath("$.blockers.length()").value(3))
                .andExpect(jsonPath("$.blockers[0].groupBuyId").value(openOwn))
                .andExpect(jsonPath("$.blockers[0].title").value("내가 연 공구"))
                .andExpect(jsonPath("$.blockers[0].role").value("ORGANIZER"))
                .andExpect(jsonPath("$.blockers[0].status").value("OPEN"))
                .andExpect(jsonPath("$.blockers[1].groupBuyId").value(openJoined))
                .andExpect(jsonPath("$.blockers[1].role").value("PARTICIPANT"))
                .andExpect(jsonPath("$.blockers[1].status").value("OPEN"))
                .andExpect(jsonPath("$.blockers[2].groupBuyId").value(pending))
                .andExpect(jsonPath("$.blockers[2].role").value("PARTICIPANT"))
                .andExpect(jsonPath("$.blockers[2].status").value("PENDING_CLOSE"));

        assertThat(records()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM member WHERE id = ?", Boolean.class, ME)).isTrue();
        mockMvc.perform(get("/api/auth/me").with(as(ME))).andExpect(status().isOk());
        // 비밀번호는 맞았으므로 로그인처럼 실패 횟수를 지운다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt", Integer.class)).isZero();
    }

    @Test
    void anOpenGroupBuyIOrganizeBlocksEvenWithoutMyParticipationRow() throws Exception {
        long openOwn = groupBuy().organizer(ME).insert(jdbc);
        withdraw(PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[0].groupBuyId").value(openOwn))
                .andExpect(jsonPath("$.blockers[0].role").value("ORGANIZER"));
    }

    @Test
    void onlyFinishedGroupBuysDoNotBlock() throws Exception {
        for (String s : List.of("COMPLETED", "FAILED", "CANCELLED")) {
            long id = groupBuy().organizer(ME).status(s).insert(jdbc);
            participate(id, ME, 9);
            long other = groupBuy().status(s).insert(jdbc);
            participate(other, ME, 1);
        }
        withdraw(PASSWORD).andExpect(status().isNoContent());
    }
}
