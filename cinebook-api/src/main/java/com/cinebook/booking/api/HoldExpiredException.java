package com.cinebook.booking.api;

/**
 * Nam o api chu khong phai domain: no la mot phan hop dong cong khai cua
 * BookingConfirmation — phia payment phai bat duoc no de kich hoat hoan tien.
 */
public class HoldExpiredException extends RuntimeException {

    public HoldExpiredException() {
        super("Thoi gian giu ghe da het, vui long chon lai");
    }
}
