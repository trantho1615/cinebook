package com.cinebook.catalog.domain.venue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {

    public enum Status {
        ACTIVE,
        MAINTENANCE
    }

    @Id
    private UUID id;

    // Luu khoa ngoai dang UUID chu khong dung @ManyToOne: catalog khong can duyet
    // nguoc tu phong sang rap trong bat ky luong nao, va tranh duoc lazy loading
    // ngoai tam kiem soat cua transaction.
    @Column(name = "cinema_id", nullable = false)
    private UUID cinemaId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Room() {
    }

    public static Room create(UUID cinemaId, String name, RoomType roomType) {
        Room room = new Room();
        room.id = UUID.randomUUID();
        room.cinemaId = cinemaId;
        room.name = name;
        room.roomType = roomType;
        room.status = Status.ACTIVE;
        room.createdAt = Instant.now();
        return room;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCinemaId() {
        return cinemaId;
    }

    public String getName() {
        return name;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public Status getStatus() {
        return status;
    }
}
