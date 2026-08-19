package com.cinebook.payment.web.dto;

import com.cinebook.payment.infra.InitiatePaymentUseCase;

import java.util.UUID;

public record PaymentResponse(UUID paymentId, String redirectUrl, long amount) {

    public static PaymentResponse from(InitiatePaymentUseCase.Result result) {
        return new PaymentResponse(result.paymentId(), result.redirectUrl(), result.amount());
    }
}
