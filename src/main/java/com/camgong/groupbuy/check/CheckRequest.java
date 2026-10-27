package com.camgong.groupbuy.check;

import jakarta.validation.constraints.NotNull;

/** 확인 지정(true) 또는 되돌리기(false). 토글이 아니라 목표 상태를 보내서 더블 클릭·재시도가 안전하다. */
public record CheckRequest(@NotNull Boolean confirmed) {
}
