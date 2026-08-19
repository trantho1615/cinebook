package com.cinebook.payment.domain;

import java.util.UUID;

/**
 * Cong thanh toan an sau interface nay.
 *
 * MockPaymentGateway lam truoc va LUON duoc giu lai: no cho phep chay test va demo
 * ma khong phu thuoc sandbox cua ben thu ba. VNPay se la mot cai dat them, khong
 * phai vat thay the.
 */
public interface PaymentGateway {

    String providerName();

    Session createSession(UUID paymentId, long amount, String bookingCode);

    record Session(String redirectUrl, String providerTxnId) {
    }
}
