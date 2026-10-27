package com.camgong.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class VerificationMailListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailListener.class);

    private final MailSender mailSender;
    private final AuthProperties props;

    VerificationMailListener(MailSender mailSender, AuthProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(VerificationMailRequested event) {
        try {
            mailSender.sendVerification(event.email(), props.verifyLinkBaseUrl() + event.token());
        } catch (RuntimeException e) {
            // 토큰·링크는 남기지 않는다. 사용자는 재발송으로 복구한다.
            log.warn("verification mail failed to={} cause={}", mask(event.email()), e.getClass().getSimpleName());
        }
    }

    /** 로그에는 주소 전체 대신 "a***@도메인" 만 남긴다. */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
