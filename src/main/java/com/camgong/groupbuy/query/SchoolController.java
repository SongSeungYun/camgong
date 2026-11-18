package com.camgong.groupbuy.query;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.camgong.auth.Keys;
import com.camgong.common.ApiException;
import com.camgong.groupbuy.GroupBuyRepository;

/** 학교 검색(009, 인증 불필요). 비로그인 목록의 학교 필터가 고를 학교 키 후보를 준다. */
@RestController
class SchoolController {

    private static final int MAX_RESULTS = 10;

    record Schools(List<String> items) {
    }

    private final GroupBuyRepository groupBuys;

    SchoolController(GroupBuyRepository groupBuys) {
        this.groupBuys = groupBuys;
    }

    // ponytail: 공구 행 전체의 학교 키를 훑는다(DISTINCT). 공구가 아주 많아지면 학교 키 테이블을 따로 둔다.
    @GetMapping("/api/schools")
    Schools search(@RequestParam(required = false) String q) {
        String word = q == null ? "" : Keys.normalize(q);
        if (word.isEmpty() || Keys.length(word) > GroupBuyListQuery.MAX_QUERY_LENGTH) {
            throw GroupBuyListQuery.badRequest(List.of(new ApiException.Field("q", "검색어는 1~50자여야 합니다.")));
        }
        String pattern = "%" + GroupBuySearchRepository.escapeLike(word) + "%";
        return new Schools(groupBuys.findSchools(pattern, Limit.of(MAX_RESULTS)));
    }
}
