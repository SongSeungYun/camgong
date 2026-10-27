package com.camgong.groupbuy.close;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 잘못된 스케줄러 설정은 조용히 지나가지 않고 기동에서 거부된다. */
class ClosePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(CloseProperties.Binding.class))
            .withPropertyValues("camgong.scheduler.close.enabled=true", "camgong.scheduler.close.interval=30s",
                    "camgong.scheduler.close.margin=1s", "camgong.scheduler.close.batch-size=100",
                    "camgong.scheduler.close.max-rounds=50");

    @Test
    void validValuesBind() {
        runner.run(ctx -> assertThat(ctx.getBean(CloseProperties.class).batchSize()).isEqualTo(100));
    }

    @Test
    void zeroOrNegativeBatchSizeAndRoundsAreRejected() {
        runner.withPropertyValues("camgong.scheduler.close.batch-size=0").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("camgong.scheduler.close.batch-size=-5").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("camgong.scheduler.close.max-rounds=0").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void nonPositiveIntervalAndNegativeMarginAreRejected() {
        runner.withPropertyValues("camgong.scheduler.close.interval=0s").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("camgong.scheduler.close.margin=-1s").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("camgong.scheduler.close.margin=0s").run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
