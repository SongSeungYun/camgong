package com.camgong.groupbuy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 같은 회원이 같은 입력으로 10초 안에 다시 개설하는 것을 막는다(research 결정 3).
 * 동시 요청은 PK 충돌 행의 InnoDB 락으로 직렬화되어 하나만 통과하고,
 * 개설 트랜잭션이 롤백되면 가드도 함께 풀린다.
 */
// ponytail: MySQL 전용 문법(행 별칭 AS new, 8.0.19+), 가드 행 정리 작업 없음
@Component
public class CreateGuard {

    // 영향 행 수: 1 = 신규, 2 = 10초 지난 가드 갱신(통과), 0 = 10초 안 중복.
    // useAffectedRows=true 가 아니면 0 과 1 이 구분되지 않는다(docs/spikes.md #1).
    // 행 별칭을 쓰면 기존 값은 테이블 이름을 붙여야 한다(안 붙이면 created_at 이 모호하다는 오류).
    private static final String SQL = """
            INSERT INTO create_guard (member_id, fingerprint, created_at) VALUES (:member, :fingerprint, :now) AS new
            ON DUPLICATE KEY UPDATE created_at =
                IF(create_guard.created_at < :now - INTERVAL 10 SECOND, new.created_at, create_guard.created_at)
            """;

    private final JdbcClient jdbc;

    public CreateGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 통과하면 true, 10초 안 중복이면 false. 시각은 DB NOW()가 아니라 Clock 에서 받는다. */
    public boolean pass(long memberId, String fingerprint, Instant now) {
        int affected = jdbc.sql(SQL)
                .param("member", memberId)
                .param("fingerprint", fingerprint)
                .param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)) // DB 는 UTC 로 저장한다
                .update();
        return affected > 0;
    }

    /** 정규화(trim, 설명 null→"")한 입력값 전체의 SHA-256 hex. 값 경계가 섞이지 않게 구분자를 둔다. */
    public static String fingerprint(GroupBuyRequest r) {
        String joined = String.join("\u001F",
                text(r.title()), text(r.description()), text(r.itemName()),
                Objects.toString(r.unitPrice(), ""), Objects.toString(r.totalQuantity(), ""),
                r.deadline() == null ? "" : r.deadline().toInstant().toString(),
                Objects.toString(r.organizerQuantity(), ""), Objects.toString(r.category(), ""),
                text(r.itemUrl()), text(r.openChatUrl()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }
}
