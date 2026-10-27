package com.camgong.groupbuy.check;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.camgong.auth.CurrentMember;

@RestController
@RequestMapping("/api/groupbuys/{id}")
class ParticipationCheckController {

    private final ParticipationCheckService service;

    ParticipationCheckController(ParticipationCheckService service) {
        this.service = service;
    }

    @GetMapping("/check-sheet")
    CheckSheet sheet(CurrentMember me, @PathVariable Long id, @RequestParam(required = false) String filter) {
        return service.getCheckSheet(id, me.id(), filter);
    }

    @PutMapping("/participations/{memberId}/payment")
    CheckRow payment(CurrentMember me, @PathVariable Long id, @PathVariable Long memberId,
            @Valid @RequestBody CheckRequest request) {
        return service.setPayment(id, me.id(), memberId, request.confirmed());
    }

    @PutMapping("/participations/{memberId}/receipt")
    CheckRow receipt(CurrentMember me, @PathVariable Long id, @PathVariable Long memberId,
            @Valid @RequestBody CheckRequest request) {
        return service.setReceipt(id, me.id(), memberId, request.confirmed());
    }
}
