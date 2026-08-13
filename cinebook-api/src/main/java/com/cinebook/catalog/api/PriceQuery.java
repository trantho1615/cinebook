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
}
