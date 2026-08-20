package com.cinebook.worker.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Dung du lieu toi thieu bang SQL truc tiep.
 *
 * Test cua cinebook-api dung REST de dung du lieu, nhung cinebook-worker khong co
 * endpoint nao — do la ca diem cua no. Chen thang vao bang la cach re nhat de co mot don
 * that voi ghe that cho listener xu ly.
 */
public final class WorkerDataFixture {

    private WorkerDataFixture() {
    }

    /**
     * @return id cua don vua tao
     */
    public static UUID donDaXacNhan(JdbcTemplate db, String email) {
        UUID userId = UUID.randomUUID();
        db.update("""
                INSERT INTO users (id, email, password_hash, full_name, phone, role, status,
                                   created_at, updated_at)
                VALUES (?::uuid, ?, 'x', 'Nguoi Dung', '0900000000', 'CUSTOMER', 'ACTIVE',
                        now(), now())
                """, userId.toString(), email);

        UUID movieId = UUID.randomUUID();
        db.update("""
                INSERT INTO movies (id, title, duration_min, age_rating, release_date, status,
                                    created_at, updated_at)
                VALUES (?::uuid, 'Phim Test', 120, 'T16', DATE '2026-08-01', 'NOW_SHOWING',
                        now(), now())
                """, movieId.toString());

        UUID cinemaId = UUID.randomUUID();
        db.update("""
                INSERT INTO cinemas (id, name, address, district, city, status, created_at)
                VALUES (?::uuid, 'CGV Quan 1', '123 ABC', 'Quan 1', 'Ho Chi Minh', 'ACTIVE', now())
                """, cinemaId.toString());

        UUID roomId = UUID.randomUUID();
        db.update("""
                INSERT INTO rooms (id, cinema_id, name, room_type, status, created_at)
                VALUES (?::uuid, ?::uuid, 'Phong 1', 'STANDARD', 'ACTIVE', now())
                """, roomId.toString(), cinemaId.toString());

        UUID seatId = UUID.randomUUID();
        db.update("""
                INSERT INTO seats (id, room_id, row_label, seat_number, seat_type)
                VALUES (?::uuid, ?::uuid, 'A', 1, 'STANDARD')
                """, seatId.toString(), roomId.toString());

        UUID showtimeId = UUID.randomUUID();
        db.update("""
                INSERT INTO showtimes (id, movie_id, room_id, start_at, end_at, base_price,
                                       status, created_at)
                VALUES (?::uuid, ?::uuid, ?::uuid, now() + interval '2 days',
                        now() + interval '2 days 2 hours', 90000, 'SCHEDULED', now())
                """, showtimeId.toString(), movieId.toString(), roomId.toString());

        UUID bookingId = UUID.randomUUID();
        db.update("""
                INSERT INTO bookings (id, code, user_id, showtime_id, status, total_amount,
                                      created_at, confirmed_at)
                VALUES (?::uuid, ?, ?::uuid, ?::uuid, 'CONFIRMED', 90000, now(), now())
                """, bookingId.toString(), "CB" + bookingId.toString().substring(0, 8).toUpperCase(),
                userId.toString(), showtimeId.toString());

        db.update("""
                INSERT INTO booking_items (id, booking_id, seat_id, seat_label, unit_price)
                VALUES (?::uuid, ?::uuid, ?::uuid, 'A1', 90000)
                """, UUID.randomUUID().toString(), bookingId.toString(), seatId.toString());

        return bookingId;
    }

    public static void donDep(JdbcTemplate db) {
        db.execute("TRUNCATE TABLE outbox_events, notifications, booking_items, seat_hold, "
                + "bookings CASCADE");
        db.execute("TRUNCATE TABLE showtimes CASCADE");
        db.execute("TRUNCATE TABLE seats, rooms, cinemas CASCADE");
        db.execute("TRUNCATE TABLE movies CASCADE");
        db.execute("TRUNCATE TABLE users CASCADE");
    }
}
