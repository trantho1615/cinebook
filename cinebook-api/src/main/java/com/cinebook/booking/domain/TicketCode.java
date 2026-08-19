package com.cinebook.booking.domain;

import java.security.SecureRandom;

/**
 * Ma ve in tren ve, dung de quet tai cua rap. Khac voi ma dat ve: mot don co mot
 * ma dat ve nhung moi ghe co mot ma ve rieng.
 */
public final class TicketCode {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TicketCode() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder("TK");
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
