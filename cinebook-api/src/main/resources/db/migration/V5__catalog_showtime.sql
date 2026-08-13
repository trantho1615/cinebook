CREATE TABLE showtimes (
    id         uuid        PRIMARY KEY,
    movie_id   uuid        NOT NULL REFERENCES movies (id),
    room_id    uuid        NOT NULL REFERENCES rooms (id),
    start_at   timestamptz NOT NULL,
    end_at     timestamptz NOT NULL,
    base_price bigint      NOT NULL,
    status     varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,

    CONSTRAINT ck_showtimes_time   CHECK (end_at > start_at),
    CONSTRAINT ck_showtimes_price  CHECK (base_price > 0),
    CONSTRAINT ck_showtimes_status CHECK (status IN ('SCHEDULED', 'CANCELLED'))
);

CREATE INDEX idx_showtimes_movie_start ON showtimes (movie_id, start_at);
CREATE INDEX idx_showtimes_room_start  ON showtimes (room_id, start_at);

-- Chan xep hai suat chong gio trong cung mot phong.
--
-- Vi sao khong kiem tra bang code ung dung: giua luc SELECT kiem tra va luc INSERT,
-- mot transaction khac co the chen suat chong gio. Rang buoc nay dung ke ca khi hai
-- admin bam cung luc.
--
-- tstzrange mac dinh la nua mo [start, end) nen suat bat dau DUNG LUC suat truoc
-- ket thuc khong bi coi la chong.
--
-- Menh de WHERE khien rang buoc chi ap dung cho suat con hieu luc: suat da huy
-- khong duoc phep chan viec xep lai vao dung khung gio do.
--
-- Can extension btree_gist (da cai o V1__baseline.sql) de dung toan tu = voi uuid.
ALTER TABLE showtimes ADD CONSTRAINT no_overlap
    EXCLUDE USING gist (room_id WITH =, tstzrange(start_at, end_at) WITH &&)
    WHERE (status = 'SCHEDULED');
