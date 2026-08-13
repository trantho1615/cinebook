CREATE TABLE cinemas (
    id         uuid          PRIMARY KEY,
    name       varchar(150)  NOT NULL,
    address    varchar(255)  NOT NULL,
    district   varchar(100)  NOT NULL,
    city       varchar(100)  NOT NULL,
    latitude   numeric(9, 6),
    longitude  numeric(9, 6),
    status     varchar(20)   NOT NULL,
    created_at timestamptz   NOT NULL,

    CONSTRAINT ck_cinemas_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE INDEX idx_cinemas_location ON cinemas (city, district);

CREATE TABLE rooms (
    id         uuid        PRIMARY KEY,
    cinema_id  uuid        NOT NULL REFERENCES cinemas (id),
    name       varchar(50) NOT NULL,
    room_type  varchar(20) NOT NULL,
    status     varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,

    CONSTRAINT uk_rooms_cinema_name UNIQUE (cinema_id, name),
    CONSTRAINT ck_rooms_type        CHECK (room_type IN ('STANDARD', 'IMAX', 'VIP')),
    CONSTRAINT ck_rooms_status      CHECK (status IN ('ACTIVE', 'MAINTENANCE'))
);

CREATE TABLE seats (
    id          uuid        PRIMARY KEY,
    room_id     uuid        NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    row_label   varchar(2)  NOT NULL,
    seat_number int         NOT NULL,
    seat_type   varchar(20) NOT NULL,
    is_active   boolean     NOT NULL DEFAULT true,

    CONSTRAINT ck_seats_number        CHECK (seat_number > 0),
    CONSTRAINT uk_seats_room_position UNIQUE (room_id, row_label, seat_number),
    CONSTRAINT ck_seats_type          CHECK (seat_type IN ('STANDARD', 'VIP', 'COUPLE'))
);

CREATE INDEX idx_seats_room ON seats (room_id, row_label, seat_number);
