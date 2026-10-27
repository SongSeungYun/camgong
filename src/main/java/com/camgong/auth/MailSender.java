package com.camgong.auth;

public interface MailSender {

    void sendVerification(String toEmail, String verifyLink);
}
