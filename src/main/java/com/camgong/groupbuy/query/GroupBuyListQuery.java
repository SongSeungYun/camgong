package com.camgong.groupbuy.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.camgong.common.ApiException;
import com.camgong.groupbuy.GroupBuyCategory;

/** 목록 요청 파라미터. 지원하지 않는 값은 400(errors 에 파라미터 이름). */
public record GroupBuyListQuery(DisplayStatus status, GroupBuyCategory category, List<String> words, Sort sort,
        PageCursor cursor, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    static final int MAX_QUERY_LENGTH = 50;
    // ponytail: 단어는 앞 5개만 쓴다(계약은 초과 시 오류를 정하지 않았다). 오류로 바꾸려면 여기서 errors 에 q 를 더한다.
    static final int MAX_WORDS = 5;

    public enum Sort {
        LATEST("latest"), DEADLINE("deadline");

        private final String param;

        Sort(String param) {
            this.param = param;
        }

        public String param() {
            return param;
        }

        /** 알 수 없는 값은 null. */
        public static Sort parse(String value) {
            return Arrays.stream(values()).filter(s -> s.param.equals(value)).findFirst().orElse(null);
        }
    }

    public static GroupBuyListQuery of(String status, String category, String q, String sort, String cursor, String size) {
        List<ApiException.Field> errors = new ArrayList<>();

        DisplayStatus displayStatus = DisplayStatus.OPEN;
        if (status != null) {
            displayStatus = enumOrNull(DisplayStatus.class, status);
            if (displayStatus == null) {
                errors.add(new ApiException.Field("status", "지원하지 않는 상태입니다."));
            }
        }
        GroupBuyCategory groupBuyCategory = null;
        if (category != null) {
            groupBuyCategory = enumOrNull(GroupBuyCategory.class, category);
            if (groupBuyCategory == null) {
                errors.add(new ApiException.Field("category", "지원하지 않는 카테고리입니다."));
            }
        }
        List<String> words = List.of();
        if (q != null) {
            String trimmed = q.trim();
            if (trimmed.length() > MAX_QUERY_LENGTH) {
                errors.add(new ApiException.Field("q", "검색어는 50자 이하여야 합니다."));
            } else if (!trimmed.isEmpty()) {
                words = Arrays.stream(trimmed.split("\\s+")).limit(MAX_WORDS).toList();
            }
        }
        Sort parsedSort = Sort.LATEST;
        if (sort != null) {
            parsedSort = Sort.parse(sort);
            if (parsedSort == null) {
                errors.add(new ApiException.Field("sort", "지원하지 않는 정렬입니다."));
            }
        }
        int pageSize = DEFAULT_SIZE;
        if (size != null) {
            try {
                pageSize = Integer.parseInt(size.trim());
            } catch (NumberFormatException e) {
                pageSize = 0;
            }
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            errors.add(new ApiException.Field("size", "size 는 1~50 이어야 합니다."));
        }
        if (!errors.isEmpty()) {
            throw badRequest(errors);
        }
        PageCursor pageCursor = cursor == null || cursor.isBlank() ? null : PageCursor.decode(cursor, parsedSort);
        return new GroupBuyListQuery(displayStatus, groupBuyCategory, words, parsedSort, pageCursor, pageSize);
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return Arrays.stream(type.getEnumConstants()).filter(e -> e.name().equals(value)).findFirst().orElse(null);
    }

    static ApiException badRequest(List<ApiException.Field> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.", errors);
    }
}
