package com.camgong.groupbuy;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.camgong.auth.CurrentMember;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/groupbuys")
public class GroupBuyController {

    private final GroupBuyService service;

    public GroupBuyController(GroupBuyService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<GroupBuyResponse> create(
            CurrentMember me,
            @Valid @RequestBody GroupBuyRequest request) {
        GroupBuyResponse created = service.create(me.id(), request);
        return ResponseEntity.created(URI.create("/api/groupbuys/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    GroupBuyResponse get(
            CurrentMember me,
            @PathVariable Long id) {
        return service.get(me.id(), id);
    }

    @PutMapping("/{id}")
    GroupBuyResponse update(
            CurrentMember me,
            @PathVariable Long id,
            @Valid @RequestBody GroupBuyRequest request) {
        return service.update(me.id(), id, request);
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(
            CurrentMember me,
            @PathVariable Long id) {
        service.cancel(me.id(), id);
        return ResponseEntity.noContent().build();
    }
}
