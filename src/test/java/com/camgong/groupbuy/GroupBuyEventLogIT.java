package com.camgong.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.camgong.support.IntegrationTestBase;

/** 012 T011: 공구 이벤트 로그는 받는 회원 수만 남긴다(누가 어느 공구에 참여했는지가 DB 에서 지운 뒤에도 로그에 남지 않게). */
@ExtendWith(OutputCaptureExtension.class)
class GroupBuyEventLogIT extends IntegrationTestBase {

    @Test
    void eventLogsCountRecipientsWithoutListingThem(CapturedOutput output) throws Exception {
        long id = openGroupBuy();
        seedParticipant(id, PARTICIPANT, 2);
        seedParticipant(id, OUTSIDER, 3);
        cancelAs(ORGANIZER, id).andExpect(status().is2xxSuccessful());

        assertThat(output).contains("GroupBuyCancelled groupBuyId=" + id + " recipients=2")
                .doesNotContain("recipients=[");
    }
}
