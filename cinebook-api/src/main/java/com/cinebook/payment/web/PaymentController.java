package com.cinebook.payment.web;

import com.cinebook.payment.infra.InitiatePaymentUseCase;
import com.cinebook.payment.web.dto.PaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PaymentController {

    private final InitiatePaymentUseCase initiatePayment;

    public PaymentController(InitiatePaymentUseCase initiatePayment) {
        this.initiatePayment = initiatePayment;
    }

    @PostMapping("/bookings/{bookingId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse initiate(@PathVariable UUID bookingId) {
        return PaymentResponse.from(initiatePayment.initiate(bookingId));
    }
}
