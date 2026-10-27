package com.camgong.groupbuy.check;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.camgong.common.ApiException;
import com.camgong.groupbuy.GroupBuyStatus;
import com.camgong.groupbuy.join.GroupBuyHead;
import com.camgong.groupbuy.join.JoinQueryRepository;

/**
 * 개설자용 입금·수령 체크. 확인은 "목표 상태 지정"이라(true = 확인됨, false = 확인 전) 더블 클릭·재시도·동시 요청에서 결과가 한 가지다.
 * 알림 이벤트는 만들지 않는다(FR-008).
 */
@Service
public class ParticipationCheckService {

    // ponytail: 확인 시각은 조건부 UPDATE 로만 쓴다(research 결정 2). 컬럼 이름을 인자로 받아 SQL 을 조립하지 않고 네 문장을 따로 둔다.
    private static final String PAID_SET = """
            UPDATE participation SET paid_at = COALESCE(paid_at, :now)
            WHERE group_buy_id = :groupBuyId AND member_id = :memberId
              AND member_id <> (SELECT organizer_id FROM group_buy WHERE id = :groupBuyId)""";
    private static final String PAID_CLEAR = """
            UPDATE participation SET paid_at = NULL
            WHERE group_buy_id = :groupBuyId AND member_id = :memberId
              AND member_id <> (SELECT organizer_id FROM group_buy WHERE id = :groupBuyId)""";
    private static final String RECEIVED_SET = """
            UPDATE participation SET received_at = COALESCE(received_at, :now)
            WHERE group_buy_id = :groupBuyId AND member_id = :memberId
              AND member_id <> (SELECT organizer_id FROM group_buy WHERE id = :groupBuyId)""";
    private static final String RECEIVED_CLEAR = """
            UPDATE participation SET received_at = NULL
            WHERE group_buy_id = :groupBuyId AND member_id = :memberId
              AND member_id <> (SELECT organizer_id FROM group_buy WHERE id = :groupBuyId)""";

    // ponytail: 응답 오프셋은 KST 고정(다른 응답과 같다).
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final JoinQueryRepository heads;
    private final CheckQueryRepository rows;
    private final JdbcClient jdbc;
    private final Clock clock;

    ParticipationCheckService(JoinQueryRepository heads, CheckQueryRepository rows, JdbcClient jdbc, Clock clock) {
        this.heads = heads;
        this.rows = rows;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * 접근 검사. 순서: 공구 없음·취소 404 → 개설자 아님 403 → 모집 완료 아님 409. 개설자가 아닌 사람이 공구 상태를 알아내지 못하게
     * 403 을 409 보다 먼저 판정한다. 비로그인 401 은 002 의 보호 필터가 처리한다.
     * COMPLETED 는 종료 상태라 읽은 뒤에도 바뀌지 않으므로 락이 필요 없다.
     */
    GroupBuyHead requireCheckable(Long groupBuyId, Long requesterId) {
        GroupBuyHead head = heads.findHead(groupBuyId).filter(h -> h.status() != GroupBuyStatus.CANCELLED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not Found", "공구를 찾을 수 없습니다."));
        if (!head.organizerId().equals(requesterId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORGANIZER_ONLY", "개설자만 할 수 있습니다.");
        }
        if (head.status() != GroupBuyStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_CHECKABLE", "모집이 완료된 공구에서만 체크할 수 있습니다.");
        }
        return head;
    }

    @Transactional(readOnly = true)
    public CheckSheet getCheckSheet(Long groupBuyId, Long requesterId, String filter) {
        GroupBuyHead head = requireCheckable(groupBuyId, requesterId);
        List<CheckQueryRepository.Raw> raw = switch (filter == null ? "all" : filter) {
            case "all" -> rows.findAll(groupBuyId, head.organizerId());
            case "unpaid" -> rows.findUnpaid(groupBuyId, head.organizerId());
            case "unreceived" -> rows.findUnreceived(groupBuyId, head.organizerId());
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.",
                    List.of(new ApiException.Field("filter", "all, unpaid, unreceived 중 하나여야 합니다.")));
        };
        // 요약은 필터와 무관하게 개설자를 뺀 전체 기준이다.
        long[] sums = jdbc.sql("""
                SELECT COUNT(*), COUNT(paid_at), COUNT(received_at), COALESCE(SUM(quantity), 0),
                       COALESCE(SUM(CASE WHEN paid_at IS NOT NULL THEN quantity END), 0)
                FROM participation WHERE group_buy_id = :id AND member_id <> :organizerId""")
                .param("id", groupBuyId).param("organizerId", head.organizerId())
                .query((rs, i) -> new long[] { rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5) })
                .single();
        long price = head.unitPrice();
        CheckSummary summary = new CheckSummary((int) sums[0], (int) sums[1], (int) sums[2], price * sums[3], price * sums[4]);
        return new CheckSheet(summary, raw.stream().map(r -> toRow(r, price)).toList());
    }

    @Transactional
    public CheckRow setPayment(Long groupBuyId, Long requesterId, Long memberId, boolean confirmed) {
        return set(groupBuyId, requesterId, memberId, confirmed ? PAID_SET : PAID_CLEAR);
    }

    @Transactional
    public CheckRow setReceipt(Long groupBuyId, Long requesterId, Long memberId, boolean confirmed) {
        return set(groupBuyId, requesterId, memberId, confirmed ? RECEIVED_SET : RECEIVED_CLEAR);
    }

    /**
     * 접근 검사 → 개설자 본인 지정이면 400 INVALID_TARGET → 참여 행이 없으면 404 → UPDATE 한 문장. 영향 행 0 은 "이미 그 상태"일 수
     * 있어(useAffectedRows=true 에서 값이 안 바뀐 UPDATE) 영향 행 수로 오류를 만들지 않는다: 존재 확인을 UPDATE 앞에서 따로 한다.
     */
    private CheckRow set(Long groupBuyId, Long requesterId, Long memberId, String updateSql) {
        GroupBuyHead head = requireCheckable(groupBuyId, requesterId);
        if (head.organizerId().equals(memberId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET", "개설자 본인은 체크 대상이 아닙니다.",
                    List.of(new ApiException.Field("memberId", "개설자 본인은 체크 대상이 아닙니다.")));
        }
        if (rows.findOne(groupBuyId, memberId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not Found", "이 공구의 참여자가 아닙니다.");
        }
        jdbc.sql(updateSql).param("now", LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC))
                .param("groupBuyId", groupBuyId).param("memberId", memberId).update();
        return toRow(readCommitted(groupBuyId, memberId), head.unitPrice());
    }

    /**
     * 응답에 쓸 행을 **잠금 읽기**로 다시 읽는다. 이 트랜잭션의 스냅샷은 첫 읽기(접근 검사)에서 고정되는데, 동시 요청이 먼저 같은 상태로
     * 바꿔 내 UPDATE 가 아무것도 바꾸지 못하면 InnoDB 는 새 행 버전을 만들지 않아 일반 SELECT 가 스냅샷의 낡은 값을 돌려준다
     * (예: 이미 입금 확인된 행인데 응답은 null). FOR SHARE 는 방금 커밋된 최신 값을 읽는다.
     */
    private CheckQueryRepository.Raw readCommitted(Long groupBuyId, Long memberId) {
        return jdbc.sql("""
                SELECT p.member_id, m.name, p.quantity, p.paid_at, p.received_at
                FROM participation p JOIN member m ON m.id = p.member_id
                WHERE p.group_buy_id = :groupBuyId AND p.member_id = :memberId FOR SHARE""")
                .param("groupBuyId", groupBuyId).param("memberId", memberId)
                .query((rs, i) -> new CheckQueryRepository.Raw(rs.getLong(1), rs.getString(2), rs.getInt(3),
                        instant(rs.getObject(4, LocalDateTime.class)), instant(rs.getObject(5, LocalDateTime.class))))
                .single();
    }

    private static Instant instant(LocalDateTime t) {
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }

    private static CheckRow toRow(CheckQueryRepository.Raw r, long unitPrice) {
        return new CheckRow(r.memberId(), r.name(), r.quantity(), unitPrice * r.quantity(), toOffset(r.paidAt()),
                toOffset(r.receivedAt()), r.paidAt() == null && r.receivedAt() != null);
    }

    private static OffsetDateTime toOffset(Instant t) {
        return t == null ? null : t.atOffset(KST);
    }
}
