package com.camgong.groupbuy;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 개설·수정 공용 요청 본문. 단일 필드 제약만 여기서 검사하고,
 * 시계나 다른 값에 의존하는 규칙은 서비스에서 검사한다(research 결정 8).
 * 수치는 누락을 구분하려고 박싱 타입을 쓴다.
 */
public record GroupBuyRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title,

        @Size(max = 2000, message = "설명은 2000자 이하여야 합니다.")
        String description,

        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String itemName,

        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 1, message = "가격은 1원 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "가격은 1,000,000원 이하여야 합니다.")
        Integer unitPrice,

        @NotNull(message = "총 수량은 필수입니다.")
        @Min(value = 2, message = "총 수량은 2 이상이어야 합니다.")
        @Max(value = 1000, message = "총 수량은 1000 이하여야 합니다.")
        Integer totalQuantity,

        @NotNull(message = "마감 시각은 필수입니다.")
        OffsetDateTime deadline,

        @NotNull(message = "개설자 수량은 필수입니다.")
        @Min(value = 1, message = "개설자 수량은 1 이상이어야 합니다.")
        Integer organizerQuantity,

        @NotNull(message = "카테고리는 필수입니다.")
        GroupBuyCategory category,

        @NotBlank(message = "구매처 링크는 필수입니다.")
        @Size(max = 500, message = "구매처 링크는 500자 이하여야 합니다.")
        @Pattern(regexp = "^https://.+", message = "구매처 링크는 https:// 로 시작해야 합니다.")
        String itemUrl,

        @NotBlank(message = "오픈채팅 링크는 필수입니다.")
        @Size(max = 500, message = "오픈채팅 링크는 500자 이하여야 합니다.")
        @Pattern(regexp = "^https://.+", message = "오픈채팅 링크는 https:// 로 시작해야 합니다.")
        String openChatUrl) {
}
