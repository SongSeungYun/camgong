package com.camgong.groupbuy.close;

import static com.camgong.support.GroupBuySeed.groupBuy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.camgong.support.IntegrationTestBase;

/** 마감 확정 테스트 공통: 마감 T 를 시계 시작점 근처에 두고, 시계를 움직여 확정을 직접 호출한다. */
abstract class CloseTestBase extends IntegrationTestBase {

    /** 마감 시각 T: 시계 시작점 + 1시간. */
    protected static final Instant T = START.plus(Duration.ofHours(1));

    @Autowired
    protected GroupBuyCloseService closeService;

    /** 마감 T, 총 24 · 개설자 9(미충족) 인 OPEN 공구와 개설자 참여 행. */
    protected long openUnderfilled(String title) {
        return seed(title, "OPEN", T, 24, 9);
    }

    protected long seed(String title, String status, Instant deadline, int total, int secured) {
        long id = groupBuy().title(title).status(status).deadline(deadline).quantities(total, secured).insert(jdbc);
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, 1, ?, ?)", id,
                secured, LocalDateTime.ofInstant(START, ZoneOffset.UTC));
        return id;
    }

    protected void addParticipant(long groupBuyId, long memberId, int quantity) {
        jdbc.update("INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (?, ?, ?, ?)",
                groupBuyId, memberId, quantity, LocalDateTime.ofInstant(START, ZoneOffset.UTC));
        jdbc.update("UPDATE group_buy SET secured_quantity = secured_quantity + ? WHERE id = ?", quantity, groupBuyId);
    }

    protected String statusOf(long id) {
        return jdbc.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, id);
    }

    /** 잘못 확정된 공구(마감 전인데 FAILED, 또는 가득 찼는데 FAILED). 비어 있어야 정상이다. */
    protected List<Map<String, Object>> wronglyFailed(Instant asOf) {
        return jdbc.queryForList("SELECT id FROM group_buy WHERE status = 'FAILED' AND (deadline > ? OR secured_quantity = total_quantity)",
                LocalDateTime.ofInstant(asOf, ZoneOffset.UTC));
    }

    /** 공구에 참여 행이 몇 건 남았는지. */
    protected int participationRows(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM participation WHERE group_buy_id = ?", Integer.class, id);
    }
}
