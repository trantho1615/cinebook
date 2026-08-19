CREATE TABLE payments (
    id              uuid         PRIMARY KEY,
    booking_id      uuid         NOT NULL REFERENCES bookings (id),
    provider        varchar(30)  NOT NULL,
    provider_txn_id varchar(100),
    amount          bigint       NOT NULL,
    status          varchar(20)  NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,

    -- Rang buoc so 3 o spec muc 5.7: khong bao gio charge hai lan cho cung mot yeu cau.
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_payments_status CHECK (status IN
        ('INITIATED', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_payments_booking ON payments (booking_id);
-- Index rieng cho job doi soat o Task 5: chi quet giao dich con treo.
CREATE INDEX idx_payments_pending ON payments (created_at) WHERE status = 'INITIATED';
