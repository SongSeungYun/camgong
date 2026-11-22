package com.camgong.auth;

import static com.camgong.support.GroupBuySeed.groupBuy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.camgong.auth.WithdrawLedger.Entry;
import com.camgong.support.IntegrationTestBase;

/** 010 결정 5-2: 탈퇴 장부(T032 커밋 뒤 한 줄, T033 기동 때 다시 익명화, T034 정리). */
@ExtendWith(OutputCaptureExtension.class)
class WithdrawLedgerIT extends IntegrationTestBase {

    private static final long ME = 20L;
    private static final String EMAIL = "w@school.ac.kr";
    private static final String PASSWORD = "password1";
    private static final LocalDateTime JOINED = LocalDateTime.ofInstant(START, ZoneOffset.UTC);

    @Autowired
    WithdrawLedger ledger;

    @Autowired
    WithdrawLedgerReplay replay;

    @BeforeEach
    void me() {
        seedLoginMember(ME, EMAIL, "탈퇴자", PASSWORD);
    }

    private ResultActions withdraw(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/withdraw").with(as(ME)).contentType(MediaType.APPLICATION_JSON)
                .content(com.camgong.support.GroupBuyBodies.json(Map.of("password", password))));
    }

    private static LocalDateTime utc(java.time.Instant t) {
        return LocalDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    // --- T032 ---

    @Test
    void aCommittedWithdrawalAppendsOneLineWithIdJoinTimeAndWithdrawalTime() throws Exception {
        clock.set(START.plus(Duration.ofDays(3)).plusNanos(123_456_789));

        withdraw(PASSWORD).andExpect(status().isNoContent());

        assertThat(ledger.entries()).containsExactly(new Entry(ME, JOINED, LocalDateTime.parse("2026-10-30T00:00:00.123456")));
        assertThat(Files.readString(LEDGER)).doesNotContain(EMAIL).doesNotContain("탈퇴자");
    }

    @Test
    void refusedWithdrawalsWriteNothing() throws Exception {
        withdraw(" ").andExpect(status().isBadRequest());
        withdraw("wrong-password").andExpect(status().isBadRequest());
        groupBuy().organizer(ME).insert(jdbc);
        withdraw(PASSWORD).andExpect(status().isConflict());

        assertThat(ledger.entries()).isEmpty();
    }

    @Test
    void aFailedLedgerWriteStillAnswers204AndLogsAnError(CapturedOutput output) throws Exception {
        Files.delete(LEDGER);
        Files.createDirectory(LEDGER);                     // 파일 자리에 디렉터리: 붙여 쓰기가 실패한다
        try {
            withdraw(PASSWORD).andExpect(status().isNoContent());
        } finally {
            Files.delete(LEDGER);
            Files.createFile(LEDGER);
        }
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NOT NULL FROM member WHERE id = ?", Boolean.class, ME)).isTrue();
        assertThat(output.getAll().lines().filter(l -> l.contains("withdraw-ledger-write-failed memberId=20")))
                .singleElement().asString().contains("ERROR");
    }

    // --- T033 ---

    @Test
    void replayReanonymisesOnlyMembersWhoseIdAndJoinTimeMatchAndAreNotWithdrawn(CapturedOutput output) throws Exception {
        // 복구로 되살아난 탈퇴자 ME: 세션·알림·가입 신청·로그인 시도·개설 가드·설명이 모두 돌아와 있다.
        long done = groupBuy().organizer(ME).status("COMPLETED").insert(jdbc);
        jdbc.update("UPDATE group_buy SET description = '010-1234-5678' WHERE id = ?", done);
        jdbc.update("INSERT INTO login_session (token_hash, member_id, created_at, last_used_at) VALUES (SHA2('back', 256), ?, ?, ?)",
                ME, JOINED, JOINED);
        jdbc.update("INSERT INTO notification (event_id, recipient_id, type, group_buy_id, group_buy_title, created_at) "
                + "VALUES ('e1', ?, 'GROUPBUY_COMPLETED', 1, 't', ?)", ME, JOINED);
        jdbc.update("INSERT INTO pending_signup (email_key, email, token_hash, token_expires_at, last_sent_at, "
                + "window_started_at, sent_count, created_at) VALUES (?, ?, REPEAT('a', 64), ?, ?, ?, 1, ?)",
                EMAIL, EMAIL, JOINED, JOINED, JOINED, JOINED);
        jdbc.update("INSERT INTO login_attempt (email_key, failed_count, updated_at) VALUES (?, 1, ?)", EMAIL, JOINED);
        jdbc.update("INSERT INTO create_guard (member_id, fingerprint, created_at) VALUES (?, REPEAT('a', 64), ?)", ME, JOINED);
        // 되살아난 탈퇴자 21: 복구 시점에 OPEN 공구를 열고 있었다(운영자에게 WARN).
        seedLoginMember(21, "x@school.ac.kr", "열린공구", PASSWORD);
        long open = groupBuy().organizer(21).insert(jdbc);
        // 22: 덤프에도 이미 탈퇴로 남아 있다. 23: 복구 뒤 새로 가입해 장부의 번호를 받았다(가입 시각이 다르다).
        seedMember(22, "이미탈퇴");
        LocalDateTime earlier = LocalDateTime.parse("2026-10-01T00:00:00");
        jdbc.update("UPDATE member SET name = '탈퇴한 회원', email = NULL, email_key = NULL, display_name_key = NULL, withdrawn_at = ? "
                + "WHERE id = 22", earlier);
        seedMember(23, "새가입");
        jdbc.update("UPDATE member SET created_at = ? WHERE id = 23", JOINED.plusDays(10));

        LocalDateTime left = LocalDateTime.parse("2026-11-20T08:00:00.000001");
        clock.set(START.plus(Duration.ofDays(26)));        // 장부 줄은 모두 8일 안
        ledger.append(new Entry(ME, JOINED, left));
        ledger.append(new Entry(21, JOINED, left));
        ledger.append(new Entry(22, JOINED, left));
        ledger.append(new Entry(23, JOINED, left));        // 번호는 같지만 가입 시각이 다르다
        ledger.append(new Entry(99, JOINED, left));        // 덤프에 없는 회원

        assertThat(replay.replay()).isEqualTo(2);

        Map<String, Object> m = jdbc.queryForMap("SELECT * FROM member WHERE id = ?", ME);
        assertThat(m.get("name")).isEqualTo("탈퇴한 회원");
        assertThat(m.get("email")).isNull();
        assertThat(m.get("email_key")).isNull();
        assertThat(m.get("display_name_key")).isNull();
        assertThat(encoder.matches(PASSWORD, (String) m.get("password_hash"))).isFalse();
        assertThat(m.get("withdrawn_at")).isEqualTo(left);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_session WHERE member_id = ?", Integer.class, ME)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_id = ?", Integer.class, ME)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_signup", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM create_guard", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT description FROM group_buy WHERE id = ?", String.class, done)).isNull();

        assertThat(jdbc.queryForObject("SELECT withdrawn_at FROM member WHERE id = 21", LocalDateTime.class)).isEqualTo(left);
        assertThat(jdbc.queryForObject("SELECT withdrawn_at FROM member WHERE id = 22", LocalDateTime.class)).isEqualTo(earlier);
        Map<String, Object> newcomer = jdbc.queryForMap("SELECT * FROM member WHERE id = 23");
        assertThat(newcomer.get("name")).isEqualTo("새가입");
        assertThat(newcomer.get("email_key")).isEqualTo("m23@school.ac.kr");
        assertThat(newcomer.get("withdrawn_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT name FROM member WHERE id = 1", String.class)).isEqualTo("개설자");

        assertThat(output.getAll()).contains("reanonymized=2");
        assertThat(output.getAll().lines().filter(l -> l.contains("reanonymized-with-open-groupbuys memberId=21")))
                .singleElement().asString().contains("WARN").contains("groupBuyIds=[" + open + "]");
        assertThat(replay.replay()).as("두 번째는 할 일이 없다").isZero();
    }

    // --- T034 ---

    /**
     * 기동 때는 먼저 적용하고 그다음에 정리한다: 8일은 복구하는 날부터 센다. 백업이 며칠 실패했거나 오래된 사본을 복구하면 덤프가 8일보다 오래됐을 수
     * 있고, 정리를 먼저 하면 그 사이 탈퇴가 되살아난다. 오래된 줄을 적용해도 번호·가입 시각이 맞아야만 지우므로 안전하다.
     */
    @Test
    void startupReplaysEveryLineBeforePruningOldOnes() {
        clock.set(START.plus(Duration.ofDays(30)));
        ledger.append(new Entry(ME, JOINED, utc(START.plus(Duration.ofDays(21)))));   // 9일 전 탈퇴: 그래도 적용한다
        ledger.append(new Entry(2, JOINED.plusSeconds(1), utc(START.plus(Duration.ofDays(29)))));

        assertThat(replay.replay()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT withdrawn_at FROM member WHERE id = ?", LocalDateTime.class, ME))
                .isEqualTo(utc(START.plus(Duration.ofDays(21))));
        assertThat(ledger.entries()).containsExactly(new Entry(2, JOINED.plusSeconds(1), utc(START.plus(Duration.ofDays(29)))));
    }

    @Test
    void replayRunsAtStartupBeforeTheWebServerAcceptsRequests() {
        // 웹 서버는 컨텍스트 새로 고침이 끝날 때 연결을 연다. 싱글턴을 다 만든 직후(그보다 앞)에 돈다.
        assertThat(replay).isInstanceOf(org.springframework.beans.factory.SmartInitializingSingleton.class);
        ledger.append(new Entry(ME, JOINED, utc(START)));

        replay.afterSingletonsInstantiated();

        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NOT NULL FROM member WHERE id = ?", Boolean.class, ME)).isTrue();
    }
}
