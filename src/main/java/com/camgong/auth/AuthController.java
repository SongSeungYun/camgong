package com.camgong.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final SignupService signupService;
    private final SessionService sessions;

    AuthController(SignupService signupService, SessionService sessions) {
        this.signupService = signupService;
        this.sessions = sessions;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return signupService.signup(request);
    }

    @PostMapping("/verify")
    ResponseEntity<AuthResponse> verify(@Valid @RequestBody VerifyRequest request) {
        SignupService.Verified verified = signupService.verify(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessions.cookie(verified.sessionToken()).toString())
                .body(verified.response());
    }
}
