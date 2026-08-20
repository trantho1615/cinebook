package com.cinebook.catalog.api;

/**
 * Tach rieng khoi ShowtimeQuery de nguoi goi chi phu thuoc vao thu minh dung.
 * Khi phase sau them voucher va combo, module pricing duoc tach ra thi chi
 * phan implement cua interface nay thay doi.
 *
 * Nhan seatType dang String chu khong phai enum SeatType: SeatType nam trong
 * catalog.domain.venue, ma catalog.api la thu module khac duoc phep import —
 * de kieu domain lot vao chu ky interface la keo ca domain ra ngoai bien.
 */
public interface PriceQuery {

    long priceFor(long basePrice, String seatType);

    /**
     * Ban chup bang gia, doc MOT lan roi dung cho ca so do ghe.
     *
     * Vi sao can: ShowtimeQueryJdbc goi priceFor cho tung ghe, va moi lan goi lai doc lai
     * bang price_rules. Mot lan xem so do ghe 96 cho thanh 96 luot truy van, moi luot muon
     * mot connection tu pool 10. Do tai cho thay chinh cho nay lam pool can (active 10/10,
     * pending co luc 30) trong khi CPU chi 12%.
     *
     * Ban chup nay chi song trong pham vi mot lan goi — khong phai cache, nen khong co van
     * de du lieu cu.
     */
    BangGia bangGia();

    /**
     * Ba dong phu thu, doc mot lan.
     */
    record BangGia(java.util.Map<String, Long> phuThu) {

        public long priceFor(long basePrice, String seatType) {
            return basePrice + phuThu.getOrDefault(seatType, 0L);
        }
    }
}
