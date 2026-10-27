package com.camgong.auth;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 개발·테스트용. 받는 사람만 INFO, 링크는 DEBUG로만 남기고 최근 발송을 메모리에 쌓는다. */
@Component
@ConditionalOnProperty(name = "camgong.mail.sender", havingValue = "logging", matchIfMissing = true)
public class LoggingMailSender implements MailSender {

    public record Sent(String toEmail, String verifyLink) {}

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendVerification(String toEmail, String verifyLink) {
        log.info("verification mail to={}", toEmail);
        log.debug("verification link={}", verifyLink);
        sent.add(new Sent(toEmail, verifyLink));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}
