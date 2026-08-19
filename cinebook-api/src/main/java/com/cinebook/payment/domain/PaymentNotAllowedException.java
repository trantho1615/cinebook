package com.cinebook.payment.domain;

public class PaymentNotAllowedException extends RuntimeException {

    public PaymentNotAllowedException(String reason) {
        super(reason);
    }
}
