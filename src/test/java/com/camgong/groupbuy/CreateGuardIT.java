package com.camgong.groupbuy;

import static com.camgong.support.GroupBuyBodies.json;
import static com.camgong.support.GroupBuyBodies.valid;
import static com.camgong.support.GroupBuyBodies.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.camgong.support.IntegrationTestBase;

/** quickstart 시나리오 7, 8 + CreateGuard 단위 동작(영향 행 0/1/2) */
class CreateGuardIT extends IntegrationTestBase {

    @Autowired
    CreateGuard createGuard;

    @Autowired
    TransactionTemplate tx;

    private MvcResult create(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/groupbuys").with(as(ORGANIZER))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andReturn();
    }

    private int groupBuyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM group_buy", Integer.class);
    }

    @Test
    void concurrentIdenticalRequestsCreateExactlyOne() throws Exception {
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return create(valid());
            }));
        }
        ready.await();
        go.countDown();

        int created = 0;
        int duplicates = 0;
        for (Future<MvcResult> f : futures) {
            MvcResult r = f.get();
            if (r.getResponse().getStatus() == 201) {
                created++;
            } else if (r.getResponse().getStatus() == 409
                    && r.getResponse().getContentAsString().contains("DUPLICATE_REQUEST")) {
                duplicates++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(duplicates).isEqualTo(n - 1);
        assertThat(groupBuyCount()).isEqualTo(1);
    }

    @Test
    void sameRequestAfterTenSecondsIsAnotherGroupBuy() throws Exception {
        assertThat(create(valid()).getResponse().getStatus()).isEqualTo(201);
        assertThat(create(valid()).getResponse().getStatus()).isEqualTo(409);

        clock.advance(Duration.ofSeconds(11));

        assertThat(create(valid()).getResponse().getStatus()).isEqualTo(201);
        assertThat(groupBuyCount()).isEqualTo(2);
    }

    @Test
    void changingAnySingleFieldIsADifferentRequest() throws Exception {
        assertThat(create(valid()).getResponse().getStatus()).isEqualTo(201);
        assertThat(create(with(valid(), "unitPrice", 1201)).getResponse().getStatus()).isEqualTo(201);
        assertThat(groupBuyCount()).isEqualTo(2);
    }

    @Test
    void guardReportsAffectedRowsAsPassOrDuplicate() {
        String fp = "a".repeat(64);
        tx.executeWithoutResult(status -> {
            assertThat(createGuard.pass(ORGANIZER, fp, clock.instant())).isTrue();      // 신규(1)
            assertThat(createGuard.pass(ORGANIZER, fp, clock.instant())).isFalse();     // 10초 안 중복(0)
            assertThat(createGuard.pass(ORGANIZER, fp, clock.instant().plusSeconds(11))).isTrue(); // 만료 갱신(2)
        });
    }

    @Test
    void fingerprintIgnoresSurroundingSpacesAndNullDescription() {
        var base = new GroupBuyRequest("t", null, "i", 1000, 10, OffsetDateTime.parse((String) valid().get("deadline")), 2, GroupBuyCategory.FOOD,
                "https://a.com", "https://b.com");
        var padded = new GroupBuyRequest(" t ", "", " i ", 1000, 10, base.deadline(), 2, GroupBuyCategory.FOOD,
                " https://a.com ", "https://b.com");
        var other = new GroupBuyRequest("t", null, "i", 1000, 10, base.deadline(), 3, GroupBuyCategory.FOOD,
                "https://a.com", "https://b.com");

        assertThat(CreateGuard.fingerprint(padded)).isEqualTo(CreateGuard.fingerprint(base));
        assertThat(CreateGuard.fingerprint(other)).isNotEqualTo(CreateGuard.fingerprint(base));
        assertThat(CreateGuard.fingerprint(base)).hasSize(64);
    }
}
