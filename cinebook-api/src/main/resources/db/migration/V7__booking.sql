CREATE TABLE bookings (
    id              uuid        PRIMARY KEY,
    code            varchar(12) NOT NULL,
    user_id         uuid        NOT NULL REFERENCES users (id),
    showtime_id     uuid        NOT NULL REFERENCES showtimes (id),
    status          varchar(20) NOT NULL,
    total_amount    bigint      NOT NULL,
    hold_expires_at timestamptz,
    created_at      timestamptz NOT NULL,
    confirmed_at    timestamptz,
    cancelled_at    timestamptz,
    version         int         NOT NULL DEFAULT 0,

    CONSTRAINT uk_bookings_code   UNIQUE (code),
    CONSTRAINT ck_bookings_status CHECK (status IN
        ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'REFUNDED')),
    CONSTRAINT ck_bookings_total  CHECK (total_amount >= 0)
);

CREATE INDEX idx_bookings_user ON bookings (user_id, created_at DESC);
-- Index rieng cho sweeper o milestone worker: chi quet don PENDING qua han.
CREATE INDEX idx_bookings_pending_expiry ON bookings (hold_expires_at) WHERE status = 'PENDING';

CREATE TABLE seat_hold (
    id             uuid        PRIMARY KEY,
    showtime_id    uuid        NOT NULL REFERENCES showtimes (id),
    seat_id        uuid        NOT NULL REFERENCES seats (id),
    booking_id     uuid        NOT NULL REFERENCES bookings (id),
    user_id        uuid        NOT NULL REFERENCES users (id),
    status         varchar(20) NOT NULL,
    expires_at     timestamptz,
    released_at    timestamptz,
    release_reason varchar(30),
    created_at     timestamptz NOT NULL,

    CONSTRAINT ck_seat_hold_status CHECK (status IN ('HELD', 'BOOKED', 'EXPIRED')),
    CONSTRAINT ck_seat_hold_reason CHECK (release_reason IS NULL OR release_reason IN
        ('TAKEN_OVER', 'SWEPT', 'USER_CANCELLED', 'PAYMENT_FAILED'))
);

-- RANG BUOC QUAN TRONG NHAT CUA CA DU AN.
--
-- Khong bao gio hai nguoi cung giu mot ghe cua cung mot suat chieu. Vi la partial
-- index, no chi chua dong HELD/BOOKED — bang co tich luy hang tram nghin dong EXPIRED
-- thi index quyet dinh hieu nang duong nong van nho dung bang so ghe dang bi giu.
--
-- Luu y: index CHI nhin status, KHONG biet expires_at. Mot hold da qua han van chiem
-- cho trong index cho toi khi duoc UPDATE sang EXPIRED. Da kiem chung bang thuc nghiem.
CREATE UNIQUE INDEX uk_seat_active ON seat_hold (showtime_id, seat_id)
    WHERE status IN ('HELD', 'BOOKED');

CREATE INDEX idx_seat_hold_showtime ON seat_hold (showtime_id)
    WHERE status IN ('HELD', 'BOOKED');
CREATE INDEX idx_seat_hold_expiry ON seat_hold (expires_at) WHERE status = 'HELD';

CREATE TABLE booking_items (
    id          uuid       PRIMARY KEY,
    booking_id  uuid       NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    seat_id     uuid       NOT NULL REFERENCES seats (id),
    seat_label  varchar(5) NOT NULL,
    unit_price  bigint     NOT NULL,
    ticket_code varchar(20),

    CONSTRAINT uk_booking_items       UNIQUE (booking_id, seat_id),
    CONSTRAINT ck_booking_items_price CHECK (unit_price > 0)
);

-- actor_type co mat tu bay gio du phase 1 chua co AI Agent: them mot cot bay gio
-- gan nhu mien phi, con them sau thi phai backfill.
CREATE TABLE audit_log (
    id             bigserial   PRIMARY KEY,
    aggregate_type varchar(50) NOT NULL,
    aggregate_id   uuid        NOT NULL,
    action         varchar(50) NOT NULL,
    actor_type     varchar(20) NOT NULL,
    actor_id       uuid,
    payload        jsonb,
    created_at     timestamptz NOT NULL,

    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER', 'AGENT', 'SYSTEM'))
);

CREATE INDEX idx_audit_aggregate ON audit_log (aggregate_type, aggregate_id, created_at DESC);
