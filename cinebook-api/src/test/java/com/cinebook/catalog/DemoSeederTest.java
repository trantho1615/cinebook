package com.cinebook.catalog;

import com.cinebook.catalog.infra.DemoDataSeeder;
import com.cinebook.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Profile "demo" tao mot Spring context rieng, nen lop nay ton them mot lan khoi dong.
 * Doi lai la du lieu demo — thu ma toan bo buoi demo dua vao — co test canh.
 */
@ActiveProfiles("demo")
class DemoSeederTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    DemoDataSeeder seeder;

    @BeforeEach
    void reset() {
        // Khong dua vao trang thai luc context khoi dong: cac test class khac dung chung
        // container va co truncate bang cua catalog.
        truncate("audit_log", "booking_items", "seat_hold", "bookings");
        truncate("showtimes");
        truncate("seats", "rooms", "cinemas");
        truncate("movie_genres", "movies");
        seeder.run(null);
    }

    @Test
    void nap_xong_thi_co_suat_chieu_dat_duoc() {
        assertThat(soSuatTrongTuongLai()).isPositive();
    }

    @Test
    void nap_lan_hai_khong_nhan_doi_du_lieu() {
        int soPhim = db.queryForObject("SELECT count(*) FROM movies", Integer.class);
        int soSuat = db.queryForObject("SELECT count(*) FROM showtimes", Integer.class);

        seeder.run(null);

        assertThat(db.queryForObject("SELECT count(*) FROM movies", Integer.class)).isEqualTo(soPhim);
        assertThat(db.queryForObject("SELECT count(*) FROM showtimes", Integer.class)).isEqualTo(soSuat);
    }

    /**
     * Test quan trong nhat cua task nay.
     *
     * Ban cu chan bang "if (movies.count() > 0) return" nen lich chieu chi duoc nap dung
     * mot lan. Bay ngay sau, moi suat deu thuoc ve qua khu va ban demo mo len khong co gi
     * de dat. Da quan sat dung tinh huong do tren DB that: 252 suat chieu, 0 suat trong
     * tuong lai.
     */
    @Test
    void lich_chieu_cu_het_han_thi_duoc_nap_lai() {
        db.update("UPDATE showtimes SET start_at = start_at - interval '30 days', "
                + "end_at = end_at - interval '30 days'");
        assertThat(soSuatTrongTuongLai()).isZero();

        seeder.run(null);

        assertThat(soSuatTrongTuongLai()).isPositive();
    }

    /**
     * Mot suat chieu le loi trong tuong lai KHONG duoc chan viec nap lai.
     *
     * Phat hien khi chay that chu khong phai khi chay test: DB demo con dung mot suat (do
     * tay tao luc tham do API), seeder thay "van con suat trong tuong lai" roi bo qua, va
     * ban demo mo len voi 1 suat tren tong 253.
     */
    @Test
    void mot_suat_le_loi_khong_chan_viec_nap_lai() {
        db.update("UPDATE showtimes SET start_at = start_at - interval '30 days', "
                + "end_at = end_at - interval '30 days'");
        db.update("UPDATE showtimes SET start_at = now() + interval '2 days', "
                + "end_at = now() + interval '2 days 2 hours' "
                + "WHERE id = (SELECT id FROM showtimes LIMIT 1)");
        assertThat(soSuatTrongTuongLai()).isEqualTo(1);

        seeder.run(null);

        assertThat(soSuatTrongTuongLai()).isGreaterThan(1);
    }

    /**
     * Nap lai lich chieu KHONG duoc xoa lich cu: bookings va seat_hold co the dang tham
     * chieu toi chung, va mot rap co lich chieu qua khu la chuyen binh thuong.
     */
    @Test
    void nap_lai_khong_xoa_lich_chieu_cu() {
        db.update("UPDATE showtimes SET start_at = start_at - interval '30 days', "
                + "end_at = end_at - interval '30 days'");
        int soSuatCu = db.queryForObject("SELECT count(*) FROM showtimes", Integer.class);

        seeder.run(null);

        // Dem rieng nhung dong da bi day lui 30 ngay. Khong dung "start_at < now()": lan
        // nap moi cung sinh vai suat cua HOM NAY da troi qua (khung dau tien la 09:00 UTC),
        // nen con so do lon hon so suat cu — da gap dung tinh huong nay, 252 thanh 270.
        Integer conLai = db.queryForObject(
                "SELECT count(*) FROM showtimes WHERE start_at < now() - interval '20 days'",
                Integer.class);
        assertThat(conLai).isEqualTo(soSuatCu);
    }

    private Integer soSuatTrongTuongLai() {
        return db.queryForObject(
                "SELECT count(*) FROM showtimes WHERE start_at > now()", Integer.class);
    }
}
