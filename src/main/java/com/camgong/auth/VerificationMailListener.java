package com.camgong.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class VerificationMailListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailListener.class);

    private final MailSender mailSender;
    private final AuthProperties props;

    private final Executor executor;

    VerificationMailListener(MailSender mailSender, AuthProperties props, @Qualifier("mailExecutor") Executor executor) {
        this.mailSender = mailSender;
        this.props = props;
        this.executor = executor;
    }

    /**
     * 발송은 이름 붙은 mailExecutor 에 제출한다. 큐가 가득 차 거부돼도 이미 커밋된 가입·재발송 요청은 실패하지 않는다: 마스킹한 수신자와
     * 함께 WARN 만 남긴다(한도는 이미 소모됐고 재발송 토큰이 새로 만들어져 옛 메일 링크도 무효다 — 사용자는 한도가 풀린 뒤 재발송한다).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(VerificationMailRequested event) {
        try {
            executor.execute(() -> send(event));
        } catch (RuntimeException e) {
            log.warn("verification mail dropped to={} cause={}", mask(event.email()), e.getClass().getSimpleName());
        }
    }

    private void send(VerificationMailRequested event) {
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
