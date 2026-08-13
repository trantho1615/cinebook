package com.cinebook.catalog.domain.venue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cinemas")
public class Cinema {

    public enum Status {
        ACTIVE,
        CLOSED
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Cinema() {
    }

    public static Cinema create(String name, String address, String district, String city) {
        Cinema cinema = new Cinema();
        cinema.id = UUID.randomUUID();
        cinema.name = name;
        cinema.address = address;
        cinema.district = district;
        cinema.city = city;
        cinema.status = Status.ACTIVE;
        cinema.createdAt = Instant.now();
        return cinema;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getDistrict() {
        return district;
    }

    public String getCity() {
        return city;
    }

    public Status getStatus() {
        return status;
    }
}
