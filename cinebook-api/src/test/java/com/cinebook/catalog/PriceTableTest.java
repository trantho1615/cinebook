package com.cinebook.catalog;

import com.cinebook.catalog.api.PriceQuery;
import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Toi uu o Milestone 8 doi cho doc bang gia: tu 96 luot (mot luot moi ghe) xuong mot luot
 * cho ca so do ghe. Bo test nay giu cho viec do khong lam sai gia.
 *
 * Nhanh ma sai thi te hon cham ma dung.
 */
class PriceTableTest extends AbstractApiTest {

    @Autowired
    PriceQuery priceQuery;

    @Autowired
    JdbcTemplate db;

    @Test
    void ban_chup_cho_ket_qua_giong_het_duong_cu() {
        PriceQuery.BangGia bangGia = priceQuery.bangGia();

        for (String loaiGhe : new String[]{"STANDARD", "VIP", "COUPLE"}) {
            assertThat(bangGia.priceFor(90_000, loaiGhe))
                    .as("gia cho ghe %s", loaiGhe)
                    .isEqualTo(priceQuery.priceFor(90_000, loaiGhe));
        }
    }

    @Test
    void loai_ghe_la_thi_khong_cong_phu_thu() {
        assertThat(priceQuery.bangGia().priceFor(90_000, "KHONG_CO_LOAI_NAY"))
                .isEqualTo(90_000);
    }

    /**
     * Doi phu thu trong database phai co hieu luc, va phai co trong mot khoang CO GIOI HAN.
     *
     * Bao dam cu manh hon: "lan doc ke tiep thay ngay". Milestone 10 do duoc rang moi lan
     * doc bang nay ton mot vong mang 0,59 ms trong khi thoi gian thuc thi SQL chi la
     * 0,0045 ms, va them mot cache co TTL (xem PriceQueryJpa). Day la mot su NOI LONG co
     * chu y: du lieu cu bi gioi han boi TTL thay vi bien mat ngay.
     *
     * Test khong bi xoa di, no duoc viet lai de noi dung bao dam moi. Thu can chan van la
     * thu cu: gia cu ton tai VO HAN. Neu ai do bo TTL va giu cache mai mai, test nay do.
     */
    @Test
    void doi_phu_thu_trong_db_thi_thay_duoc_trong_khoang_TTL() {
        long giaCu = priceQuery.bangGia().priceFor(90_000, "VIP");

        db.update("UPDATE price_rules SET surcharge = surcharge + 5000 WHERE seat_type = 'VIP'");
        try {
            // TTL trong test la 2 giay (AbstractApiTest). Cho toi 10 giay de khong flaky tren
            // may cham, nhung van do neu cache khong bao gio het han.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(priceQuery.bangGia().priceFor(90_000, "VIP"))
                            .as("gia moi phai xuat hien sau khi TTL het han")
                            .isEqualTo(giaCu + 5000));
        } finally {
            db.update("UPDATE price_rules SET surcharge = surcharge - 5000 WHERE seat_type = 'VIP'");
        }
    }
}
