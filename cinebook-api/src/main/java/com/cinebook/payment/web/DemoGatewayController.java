package com.cinebook.payment.web;

import com.cinebook.payment.infra.WebhookSignature;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Cong thanh toan gia lap, chi ton tai o profile "demo".
 *
 * Vi sao can no: MockPaymentGateway tra redirectUrl tro toi sandbox.example.com — mot dia
 * chi khong ton tai. Trinh duyet thi KHONG the tu goi webhook, vi webhook doi chu ky HMAC
 * ma bi mat ky khong duoc phep co mat trong JavaScript. Endpoint nay dung payload, tu ky
 * phia server, roi goi thang vao handler ma cong thanh toan that goi.
 *
 * @Profile("demo") la thu ngan no khoi moi truong that: mot cong gia ma ai cung goi duoc
 * la lo hong xac nhan don ma khong tra tien. DemoGatewayAbsentTest canh dieu do bang mot
 * lop test khong bat profile demo.
 */
@RestController
@Profile("demo")
public class DemoGatewayController {

    private final WebhookSignature signature;
    private final PaymentWebhookController webhook;

    public DemoGatewayController(WebhookSignature signature, PaymentWebhookController webhook) {
        this.signature = signature;
        this.webhook = webhook;
    }

    @PostMapping("/demo/payments/{paymentId}/{ketQua}")
    public ResponseEntity<Void> hoanTat(@PathVariable UUID paymentId, @PathVariable String ketQua) {
        boolean thanhCong = "succeed".equals(ketQua);

        // eventId gan voi (paymentId, ket qua) chu khong ngau nhien: bam hai lan trong luc
        // quay demo phai duoc dedupe giong het mot cong that gui lai webhook.
        String body = """
                {"eventId":"demo-%s-%s","paymentId":"%s","status":"%s","providerTxnId":"MOCK-DEMO"}"""
                .formatted(paymentId, ketQua, paymentId, thanhCong ? "SUCCEEDED" : "FAILED");

        // Goi thang handler that thay vi tu ghi payment_events roi tu goi process: mot
        // duong duy nhat, khong co nhanh logic thu hai de lech nhau sau vai lan sua.
        return webhook.receive(body, signature.sign(body));
    }
}
