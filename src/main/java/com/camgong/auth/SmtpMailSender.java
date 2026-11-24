package com.camgong.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// ponytail: 발송 실패는 로그만 남기고 사용자가 재발송으로 복구(호출하는 리스너가 잡는다)
@Component
@ConditionalOnProperty(name = "camgong.mail.sender", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailSender implements MailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailSender(JavaMailSender mailSender, @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerification(String toEmail, String verifyLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(toEmail);
        message.setSubject("[캠공] 이메일 인증을 완료해 주세요");
        message.setText("아래 링크에서 가입을 마무리해 주세요. 링크는 24시간 안에 확인해야 합니다.\n\n" + verifyLink);
        mailSender.send(message);
    }

    /** 011 결정 6. 표시 이름은 넣지 않는다. */
    @Override
    public void sendPasswordReset(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(toEmail);
        message.setSubject("[캠공] 비밀번호 재설정");
        message.setText("아래 링크에서 새 비밀번호를 정해 주세요. 1시간 동안 한 번만 쓸 수 있어요.\n\n" + resetLink
                + "\n\n요청하지 않았다면 이 메일을 무시하세요. 비밀번호는 바뀌지 않아요.");
        mailSender.send(message);
    }
}
