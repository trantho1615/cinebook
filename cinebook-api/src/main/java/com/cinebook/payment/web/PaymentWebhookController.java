package com.cinebook.payment.web;

import com.cinebook.payment.infra.RecordWebhookUseCase;
import com.cinebook.payment.infra.WebhookSignature;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentWebhookController {

    private final WebhookSignature signature;
    private final RecordWebhookUseCase recordWebhook;

    public PaymentWebhookController(WebhookSignature signature, RecordWebhookUseCase recordWebhook) {
        this.signature = signature;
        this.recordWebhook = recordWebhook;
    }

    /**
     * Nhan @RequestBody dang String chu khong phai object da parse: chu ky duoc tinh
     * tren payload THO, parse roi serialize lai se lam chu ky khong con khop.
     */
    @PostMapping("/webhooks/payment")
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
                                        @RequestHeader(value = "X-Signature", required = false)
                                        String signatureHeader) {
        boolean hopLe = signature.verify(rawBody, signatureHeader);

        // Ghi nhan TRUOC khi kiem tra ket qua verify: payload sai chu ky cung phai
        // duoc luu lai de dieu tra.
        recordWebhook.record(rawBody, hopLe);

        if (!hopLe) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok().build();
    }
}
