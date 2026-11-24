package com.camgong.auth;

public interface MailSender {

    void sendVerification(String toEmail, String verifyLink);

    void sendPasswordReset(String toEmail, String resetLink);
}
