package com.camgong.groupbuy.query;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import tools.jackson.databind.json.JsonMapper;

import com.camgong.common.ApiException;

/**
 * 키셋 페이지의 위치를 담은 불투명 커서(JSON → Base64URL, research 결정 4).
 * 최신순은 id, 마감 임박순은 (deadline, id). 같은 마감이 여러 개일 때 id 가 없으면 넘김이 중복·누락된다.
 */
public record PageCursor(GroupBuyListQuery.Sort sort, long id, Instant deadline) {

    private static final Instant MIN_DEADLINE = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant MAX_DEADLINE = Instant.parse("2100-01-01T00:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static PageCursor latest(long id) {
        return new PageCursor(GroupBuyListQuery.Sort.LATEST, id, null);
    }

    public static PageCursor byDeadline(Instant deadline, long id) {
        return new PageCursor(GroupBuyListQuery.Sort.DEADLINE, id, deadline);
    }

    public String encode() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", sort.param());
        m.put("i", id);
        if (deadline != null) {
            m.put("d", deadline.toString());
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsString(m).getBytes(StandardCharsets.UTF_8));
    }

    /** 잘못된 문자열이거나 요청의 sort 와 맞지 않으면 400(errors[cursor]). */
    public static PageCursor decode(String raw, GroupBuyListQuery.Sort expected) {
        try {
            Map<?, ?> m = JSON.readValue(new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8), Map.class);
            GroupBuyListQuery.Sort sort = GroupBuyListQuery.Sort.parse((String) m.get("s"));
            long id = ((Number) m.get("i")).longValue();
            Instant deadline = m.get("d") == null ? null : Instant.parse((String) m.get("d"));
            // 바깥 범위의 날짜는 DB 바인딩에서 500 이 될 수 있어 형식 오류로 막는다(공구 마감은 이 범위 안에 있다).
            if (deadline != null && (deadline.isBefore(MIN_DEADLINE) || deadline.isAfter(MAX_DEADLINE))) {
                throw invalid();
            }
            if (sort == null || sort != expected || (sort == GroupBuyListQuery.Sort.DEADLINE && deadline == null)) {
                throw invalid();
            }
            return new PageCursor(sort, id, deadline);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "입력값이 올바르지 않습니다.",
                List.of(new ApiException.Field("cursor", "커서가 올바르지 않습니다.")));
    }
}
