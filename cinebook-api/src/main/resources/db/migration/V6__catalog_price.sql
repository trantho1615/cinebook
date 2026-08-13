-- Bang tra cuu phang: gia mot ghe = showtimes.base_price + price_rules.surcharge.
-- Gia theo khung gio, ngay le, hang thanh vien nam ngoai pham vi phase 1 (spec muc 5.3).
CREATE TABLE price_rules (
    seat_type varchar(20) PRIMARY KEY,
    surcharge bigint      NOT NULL,

    CONSTRAINT ck_price_rules_seat_type CHECK (seat_type IN ('STANDARD', 'VIP', 'COUPLE')),
    CONSTRAINT ck_price_rules_surcharge CHECK (surcharge >= 0)
);

INSERT INTO price_rules (seat_type, surcharge) VALUES
    ('STANDARD', 0),
    ('VIP',      20000),
    ('COUPLE',   50000);
