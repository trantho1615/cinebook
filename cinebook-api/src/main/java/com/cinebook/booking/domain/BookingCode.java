package com.cinebook.booking.domain;

import java.security.SecureRandom;

/**
 * Ma dat ve cho nguoi dung doc va doc qua dien thoai, vi du "CB7K2MQR9X".
 *
 * Bo cac ky tu de nham khi doc: 0/O, 1/I/L. Khong dung ma tu tang vi no de lo
 * so luong don dat ve cua he thong.
 */
public final class BookingCode {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingCode() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder("CB");
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
