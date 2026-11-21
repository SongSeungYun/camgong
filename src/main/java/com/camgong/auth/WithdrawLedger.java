package com.camgong.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 장부(010 plan 결정 5-2). 덤프를 복구하면 덤프 뒤의 탈퇴가 되살아나므로, 커밋된 탈퇴를 컨테이너 밖 파일에 한 줄씩 남겨 기동 때 다시 적용한다.
 * 한 줄은 {@code memberId<TAB>가입 시각<TAB>탈퇴 시각}이고 시각은 DB 와 같은 UTC 벽시계(마이크로초)다. 셋 다 DB 에 이미 있는 값이다.
 * <p>
 * 경로가 비어 있으면 기동이 실패한다: 운영에서 WITHDRAW_LEDGER 를 빠뜨린 채 조용히 돌면 복구 때 되살아난 탈퇴를 아무도 모른다.
 * 로컬·측정 프로파일은 build/ 아래 파일을, 테스트는 임시 파일을 쓴다. 파일과 디렉터리는 기동 때 만든다(쓸 수 없으면 그때 실패한다).
 */
// ponytail: 앱 하나가 파일 하나를 쓴다는 전제로 이 객체의 잠금으로만 붙이기·정리를 직렬화한다. 여러 인스턴스를 띄우면 파일 잠금이나 DB 표로 옮긴다.
@Component
public class WithdrawLedger {

    private static final Logger log = LoggerFactory.getLogger(WithdrawLedger.class);
    /** 덤프 수명 7일 + 하루(결정 5-1·5-2). 이보다 오래된 줄이 가리키는 탈퇴는 어떤 덤프에도 되살아날 수 없다. */
    static final Duration KEEP = Duration.ofDays(8);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public record Entry(long memberId, LocalDateTime createdAt, LocalDateTime withdrawnAt) {

        String line() {
            return memberId + "\t" + TIME.format(createdAt) + "\t" + TIME.format(withdrawnAt) + "\n";
        }

        /** 형식이 틀리면 null. */
        static Entry parse(String line) {
            String[] f = line.split("\t", -1);
            if (f.length != 3) {
                return null;
            }
            try {
                return new Entry(Long.parseLong(f[0]), LocalDateTime.parse(f[1], TIME), LocalDateTime.parse(f[2], TIME));
            } catch (NumberFormatException | DateTimeParseException e) {
                return null;
            }
        }
    }

    private final Path path;

    WithdrawLedger(@Value("${camgong.withdraw.ledger:}") String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("camgong.withdraw.ledger(환경변수 WITHDRAW_LEDGER)가 비어 있다: 탈퇴 장부 파일 경로를 정해야 한다");
        }
        this.path = Path.of(path.strip());
        try {
            Path parent = this.path.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            if (Files.notExists(this.path)) {
                Files.createFile(this.path);
            }
            if (!Files.isWritable(this.path)) {
                throw new IOException("쓸 수 없다");
            }
        } catch (IOException e) {
            throw new IllegalStateException("탈퇴 장부 파일을 만들거나 쓸 수 없다: " + this.path, e);
        }
    }

    /** 한 줄 붙이기. 실패는 호출한 쪽이 처리한다(탈퇴는 이미 커밋됐다). */
    synchronized void append(Entry entry) {
        try {
            Files.writeString(path, entry.line(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 읽을 수 있는 줄 전부, 파일 순서. 형식이 틀린 줄은 경고만 하고 건너뛴다. */
    synchronized List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (String line : lines()) {
            Entry e = Entry.parse(line);
            if (e == null) {
                log.warn("withdraw-ledger-malformed-line");
            } else {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * 탈퇴 시각이 now − 8일보다 이른 줄을 지운다. 같은 디렉터리의 임시 파일에 쓰고 원자적으로 바꿔 넣어, 도중에 죽어도 옛 파일이나 새 파일 둘 중
     * 하나가 온전히 남는다. 형식이 틀린 줄은 운영자가 보도록 남긴다.
     *
     * @return 지운 줄 수
     */
    synchronized int prune(Instant now) {
        LocalDateTime cutoff = LocalDateTime.ofInstant(now.minus(KEEP), ZoneOffset.UTC);
        StringBuilder kept = new StringBuilder();
        int removed = 0;
        for (String line : lines()) {
            Entry e = Entry.parse(line);
            if (e != null && e.withdrawnAt().isBefore(cutoff)) {
                removed++;
            } else {
                kept.append(line).append('\n');
            }
        }
        if (removed == 0) {
            return 0;
        }
        try {
            Path tmp = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName().toString(), ".tmp");
            try {
                Files.writeString(tmp, kept, StandardCharsets.UTF_8);
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return removed;
    }

    private List<String> lines() {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream().filter(l -> !l.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
