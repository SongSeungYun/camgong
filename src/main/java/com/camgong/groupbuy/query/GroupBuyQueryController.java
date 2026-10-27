package com.camgong.groupbuy.query;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.camgong.auth.CurrentMember;

@RestController
@RequestMapping("/api/groupbuys")
class GroupBuyQueryController {

    private final GroupBuyQueryService service;

    GroupBuyQueryController(GroupBuyQueryService service) {
        this.service = service;
    }

    /** 인증 불필요. X-Member-Id 같은 헤더는 쓰지 않는다. 파라미터는 문자열로 받아 검증 오류를 400 + errors 로 돌려준다. */
    @GetMapping
    GroupBuyQueryService.Page<GroupBuySummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size) {
        return service.list(GroupBuyListQuery.of(status, category, q, sort, cursor, size));
    }

    /** 로그인 필수(보안 설정이 막는다). 001 의 최소 상세 GET 을 대체한다. */
    @GetMapping("/{id}")
    GroupBuyDetail detail(CurrentMember me, @PathVariable Long id) {
        return service.detail(me.id(), id);
    }
}
