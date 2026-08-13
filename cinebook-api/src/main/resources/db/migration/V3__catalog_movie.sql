CREATE TABLE movies (
    id             uuid         PRIMARY KEY,
    title          varchar(255) NOT NULL,
    original_title varchar(255),
    description    text,
    duration_min   int          NOT NULL,
    age_rating     varchar(10)  NOT NULL,
    poster_url     varchar(500),
    trailer_url    varchar(500),
    release_date   date         NOT NULL,
    status         varchar(20)  NOT NULL,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,

    CONSTRAINT ck_movies_duration   CHECK (duration_min BETWEEN 1 AND 600),
    CONSTRAINT ck_movies_status     CHECK (status IN ('COMING_SOON', 'NOW_SHOWING', 'ENDED')),
    -- Phan loai theo quy dinh Viet Nam: P (moi lua tuoi), K (duoi 13 co nguoi lon di kem),
    -- T13/T16/T18 (tu 13/16/18 tuoi), C (khong duoc phep pho bien).
    CONSTRAINT ck_movies_age_rating CHECK (age_rating IN ('P', 'K', 'T13', 'T16', 'T18', 'C'))
);

CREATE INDEX idx_movies_status_release ON movies (status, release_date DESC);

-- The loai luu thanh bang phu chu khong phai mang text[]: mang PostgreSQL can cau hinh
-- kieu tuy chinh trong JPA va de vo, con bang join thi loc theo the loai la mot phep
-- join thong thuong.
CREATE TABLE movie_genres (
    movie_id uuid        NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    genre    varchar(50) NOT NULL,
    PRIMARY KEY (movie_id, genre)
);

CREATE INDEX idx_movie_genres_genre ON movie_genres (genre);
