-- Luu payload THO cua moi webhook nhan duoc, KE CA cai bi tu choi vi sai chu ky.
-- Day la thu cuu ban khi phai dieu tra mot giao dich tranh chap ba tuan sau.
CREATE TABLE payment_events (
    id                bigserial    PRIMARY KEY,
    payment_id        uuid         REFERENCES payments (id),
    provider          varchar(30)  NOT NULL,
    provider_event_id varchar(200) NOT NULL,
    raw_payload       jsonb        NOT NULL,
    signature_valid   boolean      NOT NULL,
    received_at       timestamptz  NOT NULL,
    processed_at      timestamptz,

    -- Chong xu ly trung: moi cong thanh toan deu retry, va day la cach dedupe
    -- re nhat va chac nhat — re hon nhieu so voi phan tich noi dung payload.
    CONSTRAINT uk_payment_events_provider_event UNIQUE (provider, provider_event_id)
);

CREATE INDEX idx_payment_events_payment ON payment_events (payment_id);
