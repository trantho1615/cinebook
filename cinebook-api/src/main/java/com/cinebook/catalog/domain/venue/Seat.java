package com.cinebook.catalog.domain.venue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "seats")
public class Seat {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "row_label", nullable = false, length = 2)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Seat() {
    }

    public static Seat create(UUID roomId, String rowLabel, int seatNumber, SeatType seatType) {
        Seat seat = new Seat();
        seat.id = UUID.randomUUID();
        seat.roomId = roomId;
        seat.rowLabel = rowLabel;
        seat.seatNumber = seatNumber;
        seat.seatType = seatType;
        seat.active = true;
        return seat;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Nhan ghe hien thi cho nguoi dung, vi du "F7".
     */
    public String label() {
        return rowLabel + seatNumber;
    }
}
