package com.cinebook.catalog.domain.venue;

import java.util.ArrayList;
import java.util.List;

/**
 * Sinh so do ghe cho mot phong chieu.
 *
 * Hang duoc dat ten A, B, C... tinh tu man hinh lui ve sau. Hai hang dau la STANDARD
 * (gan man hinh, gia re), hang cuoi cung la COUPLE, con lai la VIP — bo cuc thuc te
 * cua phan lon rap Viet Nam.
 */
public final class SeatLayout {

    private static final int MAX_ROWS = 26;   // A den Z
    private static final int STANDARD_ROWS_FROM_SCREEN = 2;

    private SeatLayout() {
    }

    public static List<Position> generate(int rowCount, int seatsPerRow) {
        if (rowCount < 1 || rowCount > MAX_ROWS) {
            throw new IllegalArgumentException(
                    "So hang phai tu 1 den " + MAX_ROWS + ", nhan duoc: " + rowCount);
        }
        if (seatsPerRow < 1) {
            throw new IllegalArgumentException(
                    "So ghe moi hang phai lon hon 0, nhan duoc: " + seatsPerRow);
        }

        List<Position> positions = new ArrayList<>(rowCount * seatsPerRow);
        for (int row = 0; row < rowCount; row++) {
            String label = String.valueOf((char) ('A' + row));
            SeatType type = typeForRow(row, rowCount);
            for (int number = 1; number <= seatsPerRow; number++) {
                positions.add(new Position(label, number, type));
            }
        }
        return List.copyOf(positions);
    }

    private static SeatType typeForRow(int rowIndex, int rowCount) {
        if (rowIndex < STANDARD_ROWS_FROM_SCREEN) {
            return SeatType.STANDARD;
        }
        // Hang cuoi la COUPLE, nhung chi khi phong du lon de con hang VIP o giua.
        if (rowIndex == rowCount - 1 && rowCount > STANDARD_ROWS_FROM_SCREEN + 1) {
            return SeatType.COUPLE;
        }
        return SeatType.VIP;
    }

    public record Position(String rowLabel, int seatNumber, SeatType type) {
    }
}
