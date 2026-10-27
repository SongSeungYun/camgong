package com.camgong.groupbuy.join;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 참여·수량 변경 본문. 수량의 상한은 요청 검증이 아니라 처리 시점의 남은 수량으로 판정한다. */
public record ParticipationRequest(@NotNull @Min(1) Integer quantity, @NotNull @Min(1) Integer expectedUnitPrice) {
}
