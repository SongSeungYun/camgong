package com.camgong.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final LoginService loginService;
    private final SessionService sessions;
    private final WithdrawService withdrawService;
    private final PasswordResetService resets;

    AuthController(SignupService signupService, LoginService loginService, SessionService sessions,
            WithdrawService withdrawService, PasswordResetService resets) {
        this.resets = resets;
        this.withdrawService = withdrawService;
        this.signupService = signupService;
        this.loginService = loginService;
        this.sessions = sessions;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return signupService.signup(request);
    }

    @PostMapping("/signup/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SignupResponse resend(@Valid @RequestBody ResendRequest request) {
        return signupService.resend(request);
    }

    @PostMapping("/verify")
    ResponseEntity<AuthResponse> verify(@RequestBody VerifyRequest request) {
        return withSession(signupService.verify(request));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String previousToken) {
        Authenticated authenticated = loginService.login(request);
        // 같은 브라우저에서 계정을 바꿔 로그인하면 이전 쿠키의 세션 행만 폐기한다(다른 기기의 세션은 유지, FR-012).
        // 로그인이 실패하면 여기까지 오지 않으므로 이전 세션은 그대로 남는다.
        sessions.revoke(previousToken);
        return withSession(authenticated);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String rawToken) {
        sessions.revoke(rawToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessions.clearCookie().toString())
                .build();
    }

    /** 탈퇴(010). 성공하면 이 쿠키도 지운다(다른 세션은 서비스가 모두 지운다). */
    @PostMapping("/withdraw")
    ResponseEntity<Void> withdraw(CurrentMember me, @RequestBody WithdrawRequest request) {
        withdrawService.withdraw(me.id(), request.password());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessions.clearCookie().toString())
                .build();
    }

    /** 재설정 요청(011). 가입 여부와 상관없이 같은 202. */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PasswordResetResponse requestReset(@Valid @RequestBody PasswordResetRequest request) {
        return resets.request(request);
    }

    /** 새 비밀번호 정하기(011). 쿠키는 건드리지 않는다(자동 로그인 없음). */
    @PostMapping("/password-reset/confirm")
    ResponseEntity<Void> confirmReset(@RequestBody PasswordResetConfirmRequest request) {
        resets.confirm(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    AuthResponse me(CurrentMember me) {
        return new AuthResponse(new MemberView(me.id(), me.displayName(), me.school()));
    }

    private ResponseEntity<AuthResponse> withSession(Authenticated authenticated) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessions.cookie(authenticated.sessionToken()).toString())
                .body(authenticated.response());
    }
}
