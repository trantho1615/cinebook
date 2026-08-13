CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    email         varchar(255) NOT NULL,
    password_hash varchar(100) NOT NULL,
    full_name     varchar(150) NOT NULL,
    phone         varchar(20),
    role          varchar(20)  NOT NULL,
    status        varchar(20)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,

    -- Email luon duoc chuan hoa ve chu thuong o tang ung dung truoc khi ghi,
    -- nen unique index nay chan duoc ca truong hop nguoi dung go hoa thuong lan lon.
    CONSTRAINT uk_users_email  UNIQUE (email),
    CONSTRAINT ck_users_role   CHECK (role   IN ('CUSTOMER', 'STAFF', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED'))
);
