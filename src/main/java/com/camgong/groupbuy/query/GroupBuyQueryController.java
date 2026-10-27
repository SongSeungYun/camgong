package com.camgong.groupbuy.query;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
