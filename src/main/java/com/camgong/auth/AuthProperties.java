package com.camgong.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camgong.auth")
public record AuthProperties(
        List<String> allowedEmailDomains,
        Duration sessionTtl,
        Duration sessionTouchInterval,
        Duration verifyTokenTtl,
        Mail mail,
        Login login,
        boolean cookieSecure,
        int bcryptCost,
        String verifyLinkBaseUrl,
        List<String> allowedOrigins) {

    public record Mail(Duration resendInterval, int dailyLimit) {}

    public record Login(int maxFailures, Duration lockDuration) {}
}
