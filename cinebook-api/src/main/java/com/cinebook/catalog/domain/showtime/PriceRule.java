package com.cinebook.catalog.domain.showtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_rules")
public class PriceRule {

    @Id
    @Column(name = "seat_type", length = 20)
    private String seatType;

    @Column(nullable = false)
    private long surcharge;

    protected PriceRule() {
    }

    public String getSeatType() {
        return seatType;
    }

    public long getSurcharge() {
        return surcharge;
    }
}
