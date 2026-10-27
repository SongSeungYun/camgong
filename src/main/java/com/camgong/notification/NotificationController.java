package com.camgong.notification;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.camgong.auth.CurrentMember;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    NotificationService.Page list(CurrentMember me, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size) {
        return service.list(me.id(), cursor, size);
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(CurrentMember me) {
        return Map.of("unreadCount", service.unreadCount(me.id()));
    }

    @PutMapping("/{id}/read")
    NotificationView read(CurrentMember me, @PathVariable Long id) {
        return service.markRead(me.id(), id);
    }
}
