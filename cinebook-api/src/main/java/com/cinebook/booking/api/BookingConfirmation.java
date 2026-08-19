package com.cinebook.booking.api;

import java.util.UUID;

/**
 * Hop dong duy nhat giua payment va booking.
 *
 * Bang bookings va seat_hold thuoc quyen so huu cua booking, cung voi moi bat bien
 * ma Milestone 4 dung len quanh chung. De hai module cung ghi vao do la cach nhanh
 * nhat pha vo nhung bat bien do.
 */
public interface BookingConfirmation {

    /**
     * Nem HoldExpiredException neu hold khong con hieu luc — tin hieu de phia goi
     * kich hoat luong hoan tien.
     */
    void confirm(UUID bookingId, UUID paymentId);

    void releaseAfterFailedPayment(UUID bookingId);
}
