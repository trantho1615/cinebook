package com.cinebook.payment;

import com.cinebook.payment.infra.WebhookSignature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test thuan tuy, khong can Spring context hay database.
 */
class WebhookSignatureTest {

    private final WebhookSignature signature = new WebhookSignature("bi-mat-de-test");

    @Test
    void chu_ky_dung_thi_verify_thanh_cong() {
        String body = "{\"eventId\":\"evt-1\",\"status\":\"SUCCEEDED\"}";

        assertThat(signature.verify(body, signature.sign(body))).isTrue();
    }

    @Test
    void sua_mot_ky_tu_trong_body_thi_chu_ky_khong_con_khop() {
        String body = "{\"eventId\":\"evt-1\",\"amount\":180000}";
        String chuKy = signature.sign(body);

        String bodyBiSua = body.replace("180000", "180001");

        assertThat(signature.verify(bodyBiSua, chuKy)).isFalse();
    }

    @Test
    void chu_ky_bia_dat_bi_tu_choi() {
        String body = "{\"eventId\":\"evt-1\"}";

        assertThat(signature.verify(body, "khong-phai-chu-ky")).isFalse();
        assertThat(signature.verify(body, "")).isFalse();
        assertThat(signature.verify(body, null)).isFalse();
    }

    @Test
    void bi_mat_khac_nhau_thi_sinh_chu_ky_khac_nhau() {
        String body = "{\"eventId\":\"evt-1\"}";
        WebhookSignature keGiaMao = new WebhookSignature("bi-mat-doan-mo");

        assertThat(keGiaMao.sign(body)).isNotEqualTo(signature.sign(body));
        assertThat(signature.verify(body, keGiaMao.sign(body))).isFalse();
    }
}
