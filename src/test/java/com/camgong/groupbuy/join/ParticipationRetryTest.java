package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

import com.camgong.common.ApiException;
import com.camgong.groupbuy.GroupBuyCompletion;
import com.camgong.groupbuy.GroupBuyStatus;
import com.camgong.groupbuy.ParticipationRepository;

/** 재시도 규칙(DB 없이): 충돌은 총 3회 시도하고 BUSY, 락 대기 시간 초과는 재시도 없이 즉시 BUSY, 교착은 재시도한다. */
class ParticipationRetryTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    private final QuantityGate gate = mock(QuantityGate.class);
    private final JoinQueryRepository query = mock(JoinQueryRepository.class);
    private ParticipationService service;

    @BeforeEach
    void setUp() {
        when(query.findHead(anyLong())).thenReturn(Optional.of(new GroupBuyHead(1L, GroupBuyStatus.OPEN, 1200, 9, 24,
                NOW.plusSeconds(3600))));
        when(query.findQuantity(anyLong(), anyLong())).thenReturn(Optional.empty());
        service = new ParticipationService(query, mock(ParticipationRepository.class), gate, mock(GroupBuyCompletion.class),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class));
    }

    private ApiException joinFailing() {
        return (ApiException) catchThrowable(() -> service.join(2L, 1L, new ParticipationRequest(1, 1200)));
    }

    private static Throwable catchThrowable(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @Test
    void versionConflictsAreTriedExactlyThreeTimesThenBusy() {
        when(gate.reserve(anyLong(), anyInt(), any(), any())).thenThrow(new ObjectOptimisticLockingFailureException("v", null));

        ApiException e = joinFailing();

        assertThat(e.getTitle()).isEqualTo("BUSY");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(gate, times(3)).reserve(anyLong(), anyInt(), any(), any());          // 첫 시도 + 재시도 2번
    }

    @Test
    void aLockWaitTimeoutIsNotRetried() {
        var timeout = new CannotAcquireLockException("Lock wait timeout exceeded", new SQLException("Lock wait timeout", "HY000", 1205));
        when(gate.reserve(anyLong(), anyInt(), any(), any())).thenThrow(timeout);

        ApiException e = joinFailing();

        assertThat(e.getTitle()).isEqualTo("BUSY");
        verify(gate, times(1)).reserve(anyLong(), anyInt(), any(), any());
    }

    @Test
    void aDeadlockIsRetried() {
        var deadlock = new DeadlockLoserDataAccessException("deadlock", new SQLException("Deadlock found", "40001", 1213));
        when(gate.reserve(anyLong(), anyInt(), any(), any())).thenThrow(deadlock);

        ApiException e = joinFailing();

        assertThat(e.getTitle()).isEqualTo("BUSY");
        verify(gate, times(3)).reserve(anyLong(), anyInt(), any(), any());
    }

    @Test
    void aConflictThatClearsOnTheSecondAttemptSucceeds() {
        when(gate.reserve(anyLong(), anyInt(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("v", null))
                .thenReturn(GateResult.reserved(10, 24, 1200, false));

        var joined = service.join(2L, 1L, new ParticipationRequest(1, 1200));

        assertThat(joined.created()).isTrue();
        verify(gate, times(2)).reserve(anyLong(), anyInt(), any(), any());
    }

    @Test
    void lockWaitTimeoutDetectionWalksTheCauseChain() {
        var nested = new RuntimeException("outer", new RuntimeException("mid", new SQLException("x", "HY000", 1205)));
        assertThat(ParticipationService.isLockWaitTimeout(nested)).isTrue();
        assertThat(ParticipationService.isLockWaitTimeout(new RuntimeException(new SQLException("d", "40001", 1213)))).isFalse();
        assertThat(ParticipationService.isLockWaitTimeout(new RuntimeException("plain"))).isFalse();
        assertThatThrownBy(() -> { throw new IllegalStateException(); }).isInstanceOf(IllegalStateException.class);
    }
}
