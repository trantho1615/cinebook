CREATE TABLE notifications (
    id         uuid         PRIMARY KEY,
    event_id   bigint       NOT NULL,
    booking_id uuid         NOT NULL REFERENCES bookings (id),
    recipient  varchar(255) NOT NULL,
    channel    varchar(20)  NOT NULL,
    template   varchar(50)  NOT NULL,
    status     varchar(20)  NOT NULL,
    created_at timestamptz  NOT NULL,

    -- RANG BUOC LAM NEN TINH IDEMPOTENT CUA CONSUMER.
    --
    -- Kafka giao hang at-least-once; day la thu bien "it nhat mot lan" thanh "dung mot
    -- lan" o phia nghiep vu. event_id chinh la outbox_events.id — mot id on dinh do ben
    -- gui sinh ra. Cung y tuong voi uk_seat_active va uk_payment_events.
    CONSTRAINT uk_notifications_event   UNIQUE (event_id),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_notifications_status  CHECK (status IN ('SENT', 'FAILED'))
);

CREATE INDEX idx_notifications_booking ON notifications (booking_id);
