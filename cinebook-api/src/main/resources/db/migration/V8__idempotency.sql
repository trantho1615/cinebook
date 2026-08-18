-- Bang khong co cot trang thai "dang xu ly": ban ghi chi duoc ghi SAU KHI hanh dong
-- thanh cong, va nam trong CUNG transaction voi hanh dong do. That bai thi ca hai
-- cung rollback va key duoc giai phong — client thu lai duoc.
--
-- Neu ghi key rieng khoi transaction nghiep vu thi mot lan that bai se khoa key
-- vinh vien va client khong bao gio thu lai duoc.
CREATE TABLE idempotency_keys (
    key             varchar(200) PRIMARY KEY,
    user_id         uuid         NOT NULL REFERENCES users (id),
    endpoint        varchar(200) NOT NULL,
    response_status int          NOT NULL,
    response_body   jsonb        NOT NULL,
    created_at      timestamptz  NOT NULL
);

-- Job don dep dinh ky o milestone worker se xoa key cu hon 24 gio.
CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);
