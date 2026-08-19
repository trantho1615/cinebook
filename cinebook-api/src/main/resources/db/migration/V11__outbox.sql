-- Vi sao can bang nay: neu COMMIT database roi moi goi kafkaTemplate.send(), tien
-- trinh chet giua hai lenh la MAT EVENT VINH VIEN — ve da dat nhung email khong
-- bao gio gui. Outbox bien viec ghi event thanh mot phan cua chinh transaction
-- nghiep vu. Danh doi: giao hang at-least-once, nen consumer phai idempotent.
CREATE TABLE outbox_events (
    id             bigserial   PRIMARY KEY,
    aggregate_type varchar(50) NOT NULL,
    aggregate_id   uuid        NOT NULL,
    event_type     varchar(50) NOT NULL,
    payload        jsonb       NOT NULL,
    created_at     timestamptz NOT NULL,
    published_at   timestamptz,
    attempt_count  int         NOT NULL DEFAULT 0,
    last_error     text
);

-- Rang buoc so 4 o spec muc 5.7: relay quet outbox khong scan toan bang.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;
