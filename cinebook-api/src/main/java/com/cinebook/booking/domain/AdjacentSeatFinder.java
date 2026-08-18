package com.cinebook.booking.domain;

import com.cinebook.booking.api.SeatMapEntry;
import com.cinebook.booking.api.SeatStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tim dai ghe trong lien nhau tot nhat.
 *
 * "Tot nhat" duoc dinh nghia bang hai tieu chi, theo thu tu uu tien:
 *   1. Hang cang gan giua phong cang tot (goc nhin dep nhat).
 *   2. Trong cung mot hang, dai cang gan giua hang cang tot.
 *
 * Day la ban dich cua cau "con 2 ghe trong canh nhau o hang giua" ma nguoi dung
 * that su noi — spec muc 11.
 */
public final class AdjacentSeatFinder {

    private AdjacentSeatFinder() {
    }

    public static Optional<List<SeatMapEntry>> findBest(List<SeatMapEntry> seatMap, int count) {
        if (count < 1 || seatMap == null || seatMap.isEmpty()) {
            return Optional.empty();
        }

        Map<String, List<SeatMapEntry>> theoHang = nhomTheoHang(seatMap);
        List<String> tenHang = new ArrayList<>(theoHang.keySet());
        double hangGiua = (tenHang.size() - 1) / 2.0;

        List<Ung> ungVien = new ArrayList<>();
        for (int chiSoHang = 0; chiSoHang < tenHang.size(); chiSoHang++) {
            List<SeatMapEntry> hang = theoHang.get(tenHang.get(chiSoHang));
            double giuaHang = (hang.size() - 1) / 2.0;

            for (List<SeatMapEntry> dai : timCacDai(hang, count)) {
                double viTriDai = (hang.indexOf(dai.getFirst()) + hang.indexOf(dai.getLast())) / 2.0;
                ungVien.add(new Ung(
                        Math.abs(chiSoHang - hangGiua),
                        Math.abs(viTriDai - giuaHang),
                        dai));
            }
        }

        return ungVien.stream()
                .min(Comparator.comparingDouble(Ung::lechHang).thenComparingDouble(Ung::lechCot))
                .map(Ung::ghe);
    }

    private static Map<String, List<SeatMapEntry>> nhomTheoHang(List<SeatMapEntry> seatMap) {
        List<SeatMapEntry> daSapXep = new ArrayList<>(seatMap);
        daSapXep.sort(Comparator.comparing(SeatMapEntry::rowLabel)
                .thenComparingInt(SeatMapEntry::seatNumber));

        Map<String, List<SeatMapEntry>> theoHang = new LinkedHashMap<>();
        for (SeatMapEntry seat : daSapXep) {
            theoHang.computeIfAbsent(seat.rowLabel(), k -> new ArrayList<>()).add(seat);
        }
        return theoHang;
    }

    /**
     * Quet mot hang, tra ve moi dai gom dung count ghe TRONG va LIEN TIEP nhau.
     */
    private static List<List<SeatMapEntry>> timCacDai(List<SeatMapEntry> hang, int count) {
        List<List<SeatMapEntry>> ketQua = new ArrayList<>();
        List<SeatMapEntry> dangChay = new ArrayList<>();

        for (SeatMapEntry seat : hang) {
            if (seat.status() != SeatStatus.AVAILABLE) {
                dangChay.clear();
                continue;
            }
            boolean noiTiep = !dangChay.isEmpty()
                    && seat.seatNumber() == dangChay.getLast().seatNumber() + 1;
            if (!noiTiep) {
                dangChay.clear();
            }
            dangChay.add(seat);

            if (dangChay.size() >= count) {
                ketQua.add(List.copyOf(dangChay.subList(dangChay.size() - count, dangChay.size())));
            }
        }
        return ketQua;
    }

    private record Ung(double lechHang, double lechCot, List<SeatMapEntry> ghe) {
    }
}
