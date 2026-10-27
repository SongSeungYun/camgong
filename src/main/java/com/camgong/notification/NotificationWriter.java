package com.camgong.notification;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 행을 청크 단위로 배치 삽입한다. 같은 (event_id, recipient_id) 가 이미 있으면 오류 없이 무시하므로(ON DUPLICATE KEY UPDATE id = id)
 * 같은 사건을 두 번 처리해도 대상자마다 한 건뿐이다. 중복 판정은 영향 행 수에 기대지 않는다(useAffectedRows 의존 금지).
 * 청크 하나가 실패하면 그 청크만 행마다 따로 넣어 실패한 행만 가려내고, 실패한 행은 지수 대기로 몇 번 다시 시도한 뒤 포기한다.
 */
@Component
class NotificationWriter {

    private static final Logger log = LoggerFactory.getLogger(NotificationWriter.class);

    /** 연달아 이만큼 행을 포기하면 이 사건의 나머지를 포기한다. 한 행만 계속 실패하는 경우(독 행)는 격리되어 나머지가 계속 처리된다. */
    private static final int GIVE_UPS_BEFORE_ABORT = 2;

    private static final String INSERT = """
            INSERT INTO notification (event_id, recipient_id, type, group_buy_id, group_buy_title, changed_fields, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE id = id""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final NotificationProperties props;

    NotificationWriter(JdbcTemplate jdbc, PlatformTransactionManager txManager, NotificationProperties props) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.props = props;
    }

    void write(List<NotificationRow> rows) {
        int chunk = props.insertChunkSize();
        int written = 0;
        for (int from = 0; from < rows.size(); from += chunk) {
            List<NotificationRow> part = rows.subList(from, Math.min(rows.size(), from + chunk));
            try {
                tx.executeWithoutResult(s -> batchChunk(part));
                written += part.size();
            } catch (RuntimeException e) {
                // 청크 하나의 실패가 다른 청크나 같은 청크의 다른 수신자를 막지 않는다: 이 청크만 행마다 따로 넣는다.
                // 연속 포기 횟수는 청크 안에서만 센다(청크 경계를 넘겨 세면 서로 먼 두 실패가 이벤트를 중단시킨다).
                int giveUpsInARow = 0;
                for (NotificationRow row : part) {
                    if (insertWithRetry(row)) {
                        written++;
                        giveUpsInARow = 0;
                    } else if (++giveUpsInARow >= GIVE_UPS_BEFORE_ABORT) {
                        // 한 청크 안에서 연달아 포기했다면 그 행만의 문제가 아니라 DB 장애 같은 전체 문제일 가능성이 크다. 행마다 21초씩 자며
                        // 실행기 스레드를 몇 시간 붙잡지 않도록 이 사건의 나머지는 포기한다(유실로 취급, 허용된 한계). 같은 eventId 로 다시
                        // 처리해도 (event_id, recipient_id) 유니크가 이미 쓴 행을 걸러 준다.
                        log.warn("notification-aborted eventId={} written={} unwritten={} total={} reason=consecutive-failures",
                                row.eventId(), written, rows.size() - written, rows.size());
                        return;
                    }
                }
            }
        }
    }

    /** 한 청크를 JDBC 배치로 넣는다(한 트랜잭션). 테스트가 실패를 일으키려고 가로채는 지점이다. */
    void batchChunk(List<NotificationRow> part) {
        jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                bind(ps, part.get(i));
            }

            @Override
            public int getBatchSize() {
                return part.size();
            }
        });
    }

    /** 한 행을 넣는다(자기 트랜잭션). 테스트가 특정 수신자의 실패를 일으키려고 가로채는 지점이다. */
    void insertOne(NotificationRow row) {
        tx.executeWithoutResult(s -> jdbc.update(INSERT, ps -> bind(ps, row)));
    }

    /** @return 넣었으면 true, 재시도를 모두 소진해 포기했으면 false */
    private boolean insertWithRetry(NotificationRow row) {
        RuntimeException last = null;
        Duration delay = props.retryBaseDelay();
        for (int attempt = 0; attempt <= props.retryAttempts(); attempt++) {
            if (attempt > 0) {
                pause(delay);
                delay = delay.multipliedBy(4);                      // 1초, 4초, 16초
            }
            try {
                insertOne(row);
                return true;
            } catch (RuntimeException e) {
                last = e;
            }
        }
        // 회원 이름·이메일은 남기지 않는다(id 만).
        log.warn("notification-failed eventId={} recipientId={} reason={}", row.eventId(), row.recipientId(),
                last == null ? "unknown" : last.getClass().getSimpleName());
        return false;
    }

    /** 재시도 사이의 대기. 테스트가 대기 값을 확인하려고 가로채는 지점이다. */
    void pause(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void bind(PreparedStatement ps, NotificationRow r) throws SQLException {
        ps.setString(1, r.eventId());
        ps.setLong(2, r.recipientId());
        ps.setString(3, r.type().name());
        ps.setLong(4, r.groupBuyId());
        ps.setString(5, r.groupBuyTitle());
        ps.setString(6, r.changedFields());
        ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.ofInstant(r.createdAt(), ZoneOffset.UTC)));
    }
}
