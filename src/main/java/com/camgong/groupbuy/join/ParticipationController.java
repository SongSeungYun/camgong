package com.camgong.groupbuy.join;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.camgong.auth.CurrentMember;

@RestController
@RequestMapping("/api/groupbuys/{id}/participations")
class ParticipationController {

    private final ParticipationService service;

    ParticipationController(ParticipationService service) {
        this.service = service;
    }

    /** 새 참여 201, 같은 수량으로 다시 보낸 재시도는 200. */
    @PostMapping
    ResponseEntity<ParticipationResponse> join(CurrentMember me, @PathVariable Long id,
            @Valid @RequestBody ParticipationRequest request) {
        ParticipationService.Joined joined = service.join(me.id(), id, request);
        return ResponseEntity.status(joined.created() ? HttpStatus.CREATED : HttpStatus.OK).body(joined.body());
    }

    @PutMapping("/me")
    ParticipationResponse change(CurrentMember me, @PathVariable Long id, @Valid @RequestBody ParticipationRequest request) {
        return service.change(me.id(), id, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(CurrentMember me, @PathVariable Long id) {
        service.leave(me.id(), id);
    }
}
