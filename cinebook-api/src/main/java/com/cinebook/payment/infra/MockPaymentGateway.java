package com.cinebook.payment.infra;

import com.cinebook.payment.domain.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cong thanh toan gia lap. Sinh mot redirect URL de nguoi demo bam vao.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final String baseUrl;

    public MockPaymentGateway(@Value("${cinebook.payment.mock-base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String providerName() {
        return "MOCK";
    }

    @Override
    public Session createSession(UUID paymentId, long amount, String bookingCode) {
        String providerTxnId = "MOCK-" + UUID.randomUUID();
        String redirectUrl = baseUrl + "/mock-checkout"
                + "?paymentId=" + paymentId
                + "&txn=" + providerTxnId
                + "&amount=" + amount;
        return new Session(redirectUrl, providerTxnId);
    }

    @Override
    public RemoteStatus queryStatus(String providerTxnId) {
        // Cong gia lap: moi giao dich hop le deu coi nhu da thanh cong. Cai dat that
        // se goi API tra cuu cua provider.
        return providerTxnId != null && providerTxnId.startsWith("MOCK-")
                ? RemoteStatus.SUCCEEDED
                : RemoteStatus.FAILED;
    }

    @Override
    public String refund(String providerTxnId, long amount) {
        return "MOCK-REFUND-" + UUID.randomUUID();
    }
}
