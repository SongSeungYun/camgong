package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.camgong.support.IntegrationTestBase;

import jakarta.servlet.http.Cookie;

/** 010: 커밋 뒤 세션 삭제가 실패해도 탈퇴는 이미 끝났다 — 204, 경고 로그, 남은 세션은 AuthFilter 가 받지 않는다. */
@ExtendWith(OutputCaptureExtension.class)
class WithdrawSessionCleanupFailureIT extends IntegrationTestBase {

    @MockitoSpyBean
    WithdrawService withdrawService;

    @Test
    void aDeadlockInTheWithdrawTransactionIsRetriedOnce() throws Exception {
        seedLoginMember(21, "d@school.ac.kr", "교착", "password1");
        doThrow(new org.springframework.dao.CannotAcquireLockException("Deadlock found"))
                .doCallRealMethod().when(withdrawService).anonymise(anyLong(), anyString(), any());

        mockMvc.perform(post("/api/auth/withdraw").with(as(21)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password1\"}")).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NOT NULL FROM member WHERE id = 21", Boolean.class)).isTrue();
        org.mockito.Mockito.verify(withdrawService, org.mockito.Mockito.times(2)).anonymise(anyLong(), anyString(), any());
    }

    @Test
    void aFailedSessionDeleteAfterCommitStillAnswers204AndLogs(CapturedOutput output) throws Exception {
        seedLoginMember(20, "w@school.ac.kr", "탈퇴자", "password1");
        doThrow(new IllegalStateException("DB 끊김")).when(withdrawService).deleteSessions(anyLong());

        mockMvc.perform(post("/api/auth/withdraw").with(as(20)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password1\"}")).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NOT NULL FROM member WHERE id = 20", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_session WHERE member_id = 20", Integer.class)).isOne();
        assertThat(output).contains("withdraw-session-cleanup-failed memberId=20 reason=IllegalStateException");
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("camgong_session", "test-session-20")))
                .andExpect(status().isUnauthorized());
    }

    /** 010 T015: 설명 비우기·create_guard 삭제는 익명화와 한 트랜잭션이다. 실패하면 탈퇴 전체가 되돌아간다(커밋 뒤에 따로 하면 탈퇴만 남는다). */
    @Test
    void aFailureWhileClearingMyContentRollsBackTheWholeWithdrawal() throws Exception {
        seedLoginMember(22, "c@school.ac.kr", "비우기", "password1");
        doThrow(new IllegalStateException("DB 끊김")).when(withdrawService).clearOwnContent(anyLong());

        try {
            mockMvc.perform(post("/api/auth/withdraw").with(as(22)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"password1\"}")).andExpect(status().is5xxServerError());
        } catch (jakarta.servlet.ServletException expected) {
            // 처리하지 못한 오류가 MockMvc 까지 올라와도 실패 응답이다
        }

        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM member WHERE id = 22", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT email_key FROM member WHERE id = 22", String.class)).isEqualTo("c@school.ac.kr");
    }
}
