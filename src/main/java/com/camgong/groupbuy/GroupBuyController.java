package com.camgong.groupbuy;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/groupbuys")
public class GroupBuyController {

    private final GroupBuyService service;

    public GroupBuyController(GroupBuyService service) {
        this.service = service;
    }

    // ponytail: 임시 인증(X-Member-Id), 4단계에서 로그인으로 교체
    @PostMapping
    ResponseEntity<GroupBuyResponse> create(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @Valid @RequestBody GroupBuyRequest request) {
        GroupBuyResponse created = service.create(memberId, request);
        return ResponseEntity.created(URI.create("/api/groupbuys/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    GroupBuyResponse get(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @PathVariable Long id) {
        return service.get(memberId, id);
    }

    @PutMapping("/{id}")
    GroupBuyResponse update(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody GroupBuyRequest request) {
        return service.update(memberId, id, request);
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @PathVariable Long id) {
        service.cancel(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
