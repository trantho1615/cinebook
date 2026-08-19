package com.cinebook.notification.domain;

/**
 * Noi email di ra khoi he thong.
 *
 * LoggingEmailSender lam truoc va LUON duoc giu lai: no cho phep chay test va demo ma
 * khong can tai khoan SMTP. Cung mau voi PaymentGateway o Milestone 5.
 */
public interface EmailSender {

    void send(String recipient, String subject, String body);
}
