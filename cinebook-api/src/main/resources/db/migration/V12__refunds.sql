-- Webhook den sau khi hold het han va ghe da co chu moi: khong xac nhan don duoc
-- nhung tien thi da tru. Bang nay ghi lai duong tien di nguoc.
CREATE TABLE refunds (
    id                 uuid         PRIMARY KEY,
    payment_id         uuid         NOT NULL REFERENCES payments (id),
    amount             bigint       NOT NULL,
    reason             varchar(100) NOT NULL,
    status             varchar(20)  NOT NULL,
    provider_refund_id varchar(100),
    created_at         timestamptz  NOT NULL,

    CONSTRAINT ck_refunds_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_refunds_amount CHECK (amount > 0)
);

CREATE INDEX idx_refunds_payment ON refunds (payment_id);
