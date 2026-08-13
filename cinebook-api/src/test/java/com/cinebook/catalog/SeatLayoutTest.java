package com.cinebook.catalog;

import com.cinebook.catalog.domain.venue.SeatLayout;
import com.cinebook.catalog.domain.venue.SeatType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test thuan tuy, khong can Spring context hay database.
 */
class SeatLayoutTest {

    @Test
    void sinh_dung_so_ghe_va_dat_ten_hang_tu_A() {
        List<SeatLayout.Position> positions = SeatLayout.generate(5, 10);

        assertThat(positions).hasSize(50);
        assertThat(positions.getFirst().rowLabel()).isEqualTo("A");
        assertThat(positions.getFirst().seatNumber()).isEqualTo(1);
        assertThat(positions.getLast().rowLabel()).isEqualTo("E");
        assertThat(positions.getLast().seatNumber()).isEqualTo(10);
    }

    @Test
    void hai_hang_dau_la_standard_hang_cuoi_la_couple_con_lai_la_vip() {
        List<SeatLayout.Position> positions = SeatLayout.generate(5, 4);

        assertThat(loaiCuaHang(positions, "A")).isEqualTo(SeatType.STANDARD);
        assertThat(loaiCuaHang(positions, "B")).isEqualTo(SeatType.STANDARD);
        assertThat(loaiCuaHang(positions, "C")).isEqualTo(SeatType.VIP);
        assertThat(loaiCuaHang(positions, "D")).isEqualTo(SeatType.VIP);
        assertThat(loaiCuaHang(positions, "E")).isEqualTo(SeatType.COUPLE);
    }

    @Test
    void phong_qua_nho_thi_khong_co_hang_couple() {
        List<SeatLayout.Position> positions = SeatLayout.generate(2, 4);

        assertThat(positions).hasSize(8);
        assertThat(loaiCuaHang(positions, "A")).isEqualTo(SeatType.STANDARD);
        assertThat(loaiCuaHang(positions, "B")).isEqualTo(SeatType.STANDARD);
    }

    @Test
    void so_hang_hoac_so_ghe_khong_hop_le_bi_tu_choi() {
        assertThatThrownBy(() -> SeatLayout.generate(0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeatLayout.generate(5, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeatLayout.generate(27, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SeatType loaiCuaHang(List<SeatLayout.Position> positions, String rowLabel) {
        return positions.stream()
                .filter(p -> p.rowLabel().equals(rowLabel))
                .findFirst()
                .orElseThrow()
                .type();
    }
}
