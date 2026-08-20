package com.cinebook.catalog;

import com.cinebook.catalog.api.PriceQuery;
import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

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
     * Ban chup chi song trong pham vi mot lan goi, KHONG phai cache.
     *
     * Doi phu thu trong database roi doc lai phai thay gia moi ngay — neu khong thi day da
     * thanh mot lop cache co the tra gia cu, va mot he thong ban ve tra gia cu la mot he
     * thong ban sai gia.
     */
    @Test
    void doi_phu_thu_trong_db_thi_lan_doc_ke_tiep_thay_ngay() {
        long giaCu = priceQuery.bangGia().priceFor(90_000, "VIP");

        db.update("UPDATE price_rules SET surcharge = surcharge + 5000 WHERE seat_type = 'VIP'");
        try {
            assertThat(priceQuery.bangGia().priceFor(90_000, "VIP")).isEqualTo(giaCu + 5000);
        } finally {
            db.update("UPDATE price_rules SET surcharge = surcharge - 5000 WHERE seat_type = 'VIP'");
        }
    }
}
