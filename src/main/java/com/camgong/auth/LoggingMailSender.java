package com.camgong.auth;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** 개발·테스트용. 받는 사람만 INFO, 링크는 DEBUG로만 남기고 최근 발송을 메모리에 쌓는다. */
@Component
@ConditionalOnProperty(name = "camgong.mail.sender", havingValue = "logging")
public class LoggingMailSender implements MailSender {

    public record Sent(String toEmail, String verifyLink) {}

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    private static final int KEEP = 100;

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    /** 로컬·테스트·벤치마크 프로파일에서만 쓴다. 운영에서 logging 이 켜지면 메일이 나가지 않으므로 기동을 막는다. */
    public LoggingMailSender(Environment env) {
        if (!env.acceptsProfiles(Profiles.of("local", "test", "benchmark"))) {
            throw new IllegalStateException("camgong.mail.sender=logging 은 local/test/benchmark 프로파일에서만 쓸 수 있다. 운영은 smtp 를 쓴다");
        }
    }

    @Override
    public void sendVerification(String toEmail, String verifyLink) {
        log.info("verification mail to={}", VerificationMailListener.mask(toEmail));
        log.debug("verification link={}", verifyLink);
        sent.add(new Sent(toEmail, verifyLink));
        while (sent.size() > KEEP) {                 // 테스트가 읽는 용도라 최근 몇 건만 둔다(링크 토큰이 쌓이지 않게)
            sent.remove(0);
        }
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}
