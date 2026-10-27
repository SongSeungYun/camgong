package com.camgong.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.camgong.common.ApiException;
import com.camgong.groupbuy.query.GroupBuyListQuery;
import com.camgong.groupbuy.query.PageCursor;

/** 알림함. 모든 쿼리의 조건에 recipient_id = :me 가 들어간다(FR-007: 회원은 자기 알림만 본다). 조인이 없어 JdbcClient 로 읽는다. */
@Service
public class NotificationService {

    // ponytail: 응답 오프셋은 KST 고정(다른 응답과 같다).
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final String COLUMNS = "id, type, group_buy_id, group_buy_title, changed_fields, created_at, read_at";

    public record Page(List<NotificationView> items, String nextCursor) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    NotificationService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 최근 순(id 역순) 키셋. size + 1 건을 읽어 다음 페이지 유무를 판정하고 전체 건수는 세지 않는다. */
    @Transactional(readOnly = true)
    public Page list(Long me, String cursor, String size) {
        List<ApiException.Field> errors = new ArrayList<>();
        int pageSize = GroupBuyListQuery.parseSize(size, errors);
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.", errors);
        }
        Long cursorId = cursor == null || cursor.isBlank() ? null : PageCursor.decode(cursor, GroupBuyListQuery.Sort.LATEST).id();
        List<NotificationView> rows = (cursorId == null
                ? jdbc.sql("SELECT " + COLUMNS + " FROM notification WHERE recipient_id = :me ORDER BY id DESC LIMIT :limit")
                : jdbc.sql("SELECT " + COLUMNS + " FROM notification WHERE recipient_id = :me AND id < :cursor "
                        + "ORDER BY id DESC LIMIT :limit").param("cursor", cursorId))
                .param("me", me).param("limit", pageSize + 1)
                .query((rs, i) -> view(rs.getLong("id"), rs.getString("type"), rs.getLong("group_buy_id"),
                        rs.getString("group_buy_title"), rs.getString("changed_fields"),
                        rs.getObject("created_at", LocalDateTime.class), rs.getObject("read_at", LocalDateTime.class) != null))
                .list();
        boolean hasNext = rows.size() > pageSize;
        List<NotificationView> page = hasNext ? rows.subList(0, pageSize) : rows;
        return new Page(page, hasNext ? PageCursor.latest(page.get(page.size() - 1).id()).encode() : null);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long me) {
        return jdbc.sql("SELECT COUNT(*) FROM notification WHERE recipient_id = :me AND read_at IS NULL")
                .param("me", me).query(Long.class).single();
    }

    /**
     * 읽음 처리(멱등): 읽은 시각은 처음 한 번만 정한다(COALESCE). 영향 행이 0 이면 "없음 또는 남의 것"과 "이미 읽음"을 SELECT 로 가른다 —
     * 앞의 둘은 소유 여부를 알려주지 않으려 같은 404, 뒤는 200. useAffectedRows=true 에서 값이 안 바뀐 UPDATE 는 0 행이다.
     */
    @Transactional
    public NotificationView markRead(Long me, Long id) {
        jdbc.sql("UPDATE notification SET read_at = COALESCE(read_at, :now) WHERE id = :id AND recipient_id = :me")
                .param("now", LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC)).param("id", id).param("me", me).update();
        // 접근 규칙: 내 알림이 아니면(없음 포함) 같은 404. 갱신 결과는 잠금 읽기로 읽는다(동시 요청이 먼저 읽음 처리했어도 최신 값).
        return jdbc.sql("SELECT " + COLUMNS + " FROM notification WHERE id = :id AND recipient_id = :me FOR SHARE")
                .param("id", id).param("me", me)
                .query((rs, i) -> view(rs.getLong("id"), rs.getString("type"), rs.getLong("group_buy_id"),
                        rs.getString("group_buy_title"), rs.getString("changed_fields"),
                        rs.getObject("created_at", LocalDateTime.class), rs.getObject("read_at", LocalDateTime.class) != null))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not Found", "알림을 찾을 수 없습니다."));
    }

    private static NotificationView view(long id, String type, long groupBuyId, String title, String changedFields,
            LocalDateTime createdAt, boolean read) {
        NotificationType t = NotificationType.valueOf(type);
        List<String> fields = changedFields == null || changedFields.isEmpty() ? null : Arrays.asList(changedFields.split(","));
        return new NotificationView(id, t, groupBuyId, title, fields, createdAt.atOffset(ZoneOffset.UTC).withOffsetSameInstant(KST),
                read, t != NotificationType.GROUPBUY_CANCELLED);
    }
}
