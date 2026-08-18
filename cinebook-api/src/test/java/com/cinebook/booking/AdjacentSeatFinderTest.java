package com.cinebook.booking;

import com.cinebook.booking.api.SeatMapEntry;
import com.cinebook.booking.api.SeatStatus;
import com.cinebook.booking.domain.AdjacentSeatFinder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test thuan tuy, khong can Spring context hay database.
 */
class AdjacentSeatFinderTest {

    @Test
    void tim_duoc_hai_ghe_lien_nhau_khi_ca_hang_con_trong() {
        List<SeatMapEntry> seatMap = phong(3, 6);

        Optional<List<SeatMapEntry>> ketQua = AdjacentSeatFinder.findBest(seatMap, 2);

        assertThat(ketQua).isPresent();
        assertThat(ketQua.get()).hasSize(2);
        assertThat(lienNhau(ketQua.get())).isTrue();
    }

    @Test
    void uu_tien_hang_giua_hon_hang_dau_va_hang_cuoi() {
        List<SeatMapEntry> seatMap = phong(5, 6);

        List<SeatMapEntry> ketQua = AdjacentSeatFinder.findBest(seatMap, 2).orElseThrow();

        // Phong 5 hang (A..E) thi hang giua la C
        assertThat(ketQua.getFirst().rowLabel()).isEqualTo("C");
    }

    @Test
    void bo_qua_ghe_da_bi_giu_va_khong_tra_ve_dai_bi_cat_doi() {
        List<SeatMapEntry> seatMap = new ArrayList<>(phong(1, 6));
        // Chi con {1,2} va {5,6} lien nhau; ghe 3 va 4 da co nguoi
        seatMap.set(2, doiTrangThai(seatMap.get(2), SeatStatus.HELD));
        seatMap.set(3, doiTrangThai(seatMap.get(3), SeatStatus.BOOKED));

        List<SeatMapEntry> ketQua = AdjacentSeatFinder.findBest(seatMap, 2).orElseThrow();

        assertThat(lienNhau(ketQua)).isTrue();
        assertThat(ketQua).allMatch(s -> s.status() == SeatStatus.AVAILABLE);
    }

    @Test
    void khong_du_ghe_lien_nhau_thi_tra_ve_rong() {
        List<SeatMapEntry> seatMap = new ArrayList<>(phong(1, 4));
        // Chen ke: trong, ban, trong, ban -> khong co hai ghe nao lien nhau
        seatMap.set(1, doiTrangThai(seatMap.get(1), SeatStatus.BOOKED));
        seatMap.set(3, doiTrangThai(seatMap.get(3), SeatStatus.BOOKED));

        assertThat(AdjacentSeatFinder.findBest(seatMap, 2)).isEmpty();
    }

    @Test
    void xin_mot_ghe_thi_van_hoat_dong() {
        List<SeatMapEntry> seatMap = phong(3, 4);

        assertThat(AdjacentSeatFinder.findBest(seatMap, 1)).isPresent();
    }

    private List<SeatMapEntry> phong(int soHang, int soGheMoiHang) {
        List<SeatMapEntry> ketQua = new ArrayList<>();
        for (int hang = 0; hang < soHang; hang++) {
            String label = String.valueOf((char) ('A' + hang));
            for (int so = 1; so <= soGheMoiHang; so++) {
                ketQua.add(new SeatMapEntry(UUID.randomUUID(), label, so, label + so,
                        "STANDARD", 90000L, SeatStatus.AVAILABLE));
            }
        }
        return ketQua;
    }

    private SeatMapEntry doiTrangThai(SeatMapEntry goc, SeatStatus status) {
        return new SeatMapEntry(goc.seatId(), goc.rowLabel(), goc.seatNumber(),
                goc.label(), goc.seatType(), goc.price(), status);
    }

    private boolean lienNhau(List<SeatMapEntry> ghe) {
        for (int i = 1; i < ghe.size(); i++) {
            if (!ghe.get(i).rowLabel().equals(ghe.get(i - 1).rowLabel())
                    || ghe.get(i).seatNumber() != ghe.get(i - 1).seatNumber() + 1) {
                return false;
            }
        }
        return true;
    }
}
