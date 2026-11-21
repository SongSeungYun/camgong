package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.camgong.auth.WithdrawLedger.Entry;

/** 010 결정 5-2: 탈퇴 장부 파일(T032 쓰기 형식, T034 정리, 비어 있는 설정). */
class WithdrawLedgerTest {

    @TempDir
    Path dir;

    private static final Instant NOW = Instant.parse("2026-11-22T00:00:00Z");

    private static LocalDateTime utc(Instant t) {
        return LocalDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    @Test
    void aMissingPathFailsAtStartupSoProductionCannotSilentlyRunWithoutTheLedger() {
        assertThatThrownBy(() -> new WithdrawLedger("")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WITHDRAW_LEDGER");
        assertThatThrownBy(() -> new WithdrawLedger("   ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theFileAndItsDirectoryAreCreatedAtStartupAndAnUnwritablePathFailsThen() throws Exception {
        Path path = dir.resolve("sub/withdraw-ledger.tsv");
        new WithdrawLedger(path.toString());
        assertThat(path).exists().isEmptyFile();

        Path file = Files.writeString(dir.resolve("not-a-dir"), "x");
        assertThatThrownBy(() -> new WithdrawLedger(file.resolve("ledger.tsv").toString())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void entriesRoundTripWithMicroseconds() {
        WithdrawLedger ledger = new WithdrawLedger(dir.resolve("l.tsv").toString());
        Entry a = new Entry(20, LocalDateTime.parse("2026-10-27T00:00:00"), LocalDateTime.parse("2026-11-21T09:30:00.123456"));
        Entry b = new Entry(7, LocalDateTime.parse("2026-01-02T03:04:05.000001"), LocalDateTime.parse("2026-11-21T10:00:00"));
        ledger.append(a);
        ledger.append(b);

        assertThat(ledger.entries()).containsExactly(a, b);
        assertThat(new WithdrawLedger(dir.resolve("l.tsv").toString()).entries()).containsExactly(a, b);   // 다시 떠도 그대로
    }

    @Test
    void linesOlderThanEightDaysArePrunedByReplacingTheFile() throws Exception {
        Path path = dir.resolve("l.tsv");
        WithdrawLedger ledger = new WithdrawLedger(path.toString());
        Entry old = new Entry(1, utc(NOW.minus(Duration.ofDays(30))), utc(NOW.minus(Duration.ofDays(8)).minusSeconds(1)));
        Entry edge = new Entry(2, utc(NOW.minus(Duration.ofDays(30))), utc(NOW.minus(Duration.ofDays(8))));
        Entry fresh = new Entry(3, utc(NOW.minus(Duration.ofDays(30))), utc(NOW.minus(Duration.ofDays(1))));
        ledger.append(old);
        ledger.append(edge);
        ledger.append(fresh);

        assertThat(ledger.prune(NOW)).isEqualTo(1);

        assertThat(ledger.entries()).containsExactly(edge, fresh);
        try (var files = Files.list(dir)) {
            assertThat(files).containsExactly(path);              // 바꿔 넣는 데 쓴 임시 파일이 남지 않는다
        }
        assertThat(ledger.prune(NOW)).isZero();
    }

    @Test
    void aMalformedLineIsSkippedOnReadButKeptForTheOperator() throws Exception {
        Path path = dir.resolve("l.tsv");
        WithdrawLedger ledger = new WithdrawLedger(path.toString());
        Files.writeString(path, "garbage\n");
        Entry ok = new Entry(5, utc(NOW), utc(NOW));
        ledger.append(ok);

        assertThat(ledger.entries()).containsExactly(ok);
        ledger.prune(NOW);
        assertThat(Files.readString(path)).startsWith("garbage\n");
    }
}
