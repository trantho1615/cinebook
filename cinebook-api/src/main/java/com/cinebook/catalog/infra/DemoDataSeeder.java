package com.cinebook.catalog.infra;

import com.cinebook.catalog.domain.movie.AgeRating;
import com.cinebook.catalog.domain.movie.Movie;
import com.cinebook.catalog.domain.movie.MovieStatus;
import com.cinebook.catalog.domain.showtime.Showtime;
import com.cinebook.catalog.domain.venue.Cinema;
import com.cinebook.catalog.domain.venue.Room;
import com.cinebook.catalog.domain.venue.RoomType;
import com.cinebook.catalog.domain.venue.Seat;
import com.cinebook.catalog.domain.venue.SeatLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Nap du lieu mau de chay thu bang tay va de quay video demo.
 *
 * Chi hoat dong voi profile "demo" nen khong bao gio anh huong toi test hay production.
 * Khong dung Flyway migration cho viec nay: du lieu mau khong phai schema, va mot
 * migration co du lieu mau se bi Flyway coi la bat buoc phai ton tai o moi moi truong.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final int SO_PHIM = 10;
    private static final int SUAT_MOI_NGAY_MOI_PHONG = 4;
    private static final int SO_NGAY = 7;

    private final MovieRepository movies;
    private final CinemaRepository cinemas;
    private final RoomRepository rooms;
    private final SeatRepository seats;
    private final ShowtimeRepository showtimes;

    public DemoDataSeeder(MovieRepository movies, CinemaRepository cinemas, RoomRepository rooms,
                          SeatRepository seats, ShowtimeRepository showtimes) {
        this.movies = movies;
        this.cinemas = cinemas;
        this.rooms = rooms;
        this.seats = seats;
        this.showtimes = showtimes;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (movies.count() > 0) {
            log.info("Da co du lieu, bo qua buoc nap du lieu mau");
            return;
        }

        List<Movie> danhSachPhim = napPhim();
        List<Room> danhSachPhong = napRapVaPhong();
        int soSuat = napSuatChieu(danhSachPhim, danhSachPhong);

        log.info("Da nap du lieu mau: {} phim, {} phong, {} suat chieu",
                danhSachPhim.size(), danhSachPhong.size(), soSuat);
    }

    private List<Movie> napPhim() {
        String[] tenPhim = {
                "Bao Tap Sa Mac", "Ke Trom Mat Trang 5", "Dieu Uoc Cuoi Cung",
                "Hanh Trinh Ve Phuong Bac", "Bi Mat Cua Gio", "Nguoi Gac Den Bien",
                "Cuoc Dua Ky Thu", "Tieng Vong Tu Vuc Sau", "Mua Tren Pho Co",
                "Ngay Tro Lai"
        };
        String[][] theLoai = {
                {"Hanh dong", "Phieu luu"}, {"Hoat hinh", "Hai"}, {"Tinh cam"},
                {"Phieu luu", "Gia dinh"}, {"Kinh di"}, {"Chinh kich"},
                {"Hanh dong"}, {"Kinh di", "Bi an"}, {"Tinh cam", "Chinh kich"},
                {"Gia dinh"}
        };
        int[] thoiLuong = {128, 96, 110, 135, 102, 118, 124, 99, 106, 112};

        List<Movie> ketQua = new ArrayList<>();
        for (int i = 0; i < SO_PHIM; i++) {
            Movie movie = Movie.create(
                    tenPhim[i], tenPhim[i], "Mo ta cho phim " + tenPhim[i],
                    thoiLuong[i], Set.of(theLoai[i]), AgeRating.T16,
                    "https://placehold.co/300x450?text=" + (i + 1),
                    "https://example.com/trailer/" + (i + 1),
                    LocalDate.of(2026, 8, 1).plusDays(i));
            movie.changeStatus(MovieStatus.NOW_SHOWING);
            ketQua.add(movies.save(movie));
        }
        return ketQua;
    }

    private List<Room> napRapVaPhong() {
        record ThongTinRap(String ten, String diaChi, String quan) {
        }
        List<ThongTinRap> danhSach = List.of(
                new ThongTinRap("CGV Vincom Dong Khoi", "72 Le Thanh Ton", "Quan 1"),
                new ThongTinRap("Lotte Cinema Nam Sai Gon", "469 Nguyen Huu Tho", "Quan 7"),
                new ThongTinRap("BHD Star Pham Hung", "3 Pham Hung", "Binh Chanh"));

        List<Room> ketQua = new ArrayList<>();
        for (ThongTinRap thongTin : danhSach) {
            Cinema cinema = cinemas.save(Cinema.create(
                    thongTin.ten(), thongTin.diaChi(), thongTin.quan(), "Ho Chi Minh"));

            for (int i = 1; i <= 3; i++) {
                Room room = rooms.save(Room.create(cinema.getId(), "Phong " + i, RoomType.STANDARD));
                List<Seat> danhSachGhe = SeatLayout.generate(8, 12).stream()
                        .map(p -> Seat.create(room.getId(), p.rowLabel(), p.seatNumber(), p.type()))
                        .toList();
                seats.saveAll(danhSachGhe);
                ketQua.add(room);
            }
        }
        return ketQua;
    }

    private int napSuatChieu(List<Movie> danhSachPhim, List<Room> danhSachPhong) {
        Instant batDauNgayDau = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(9, ChronoUnit.HOURS);
        int soSuat = 0;
        int chiSoPhim = 0;

        for (Room room : danhSachPhong) {
            for (int ngay = 0; ngay < SO_NGAY; ngay++) {
                Instant gioTrongNgay = batDauNgayDau.plus(ngay, ChronoUnit.DAYS);

                for (int suat = 0; suat < SUAT_MOI_NGAY_MOI_PHONG; suat++) {
                    Movie movie = danhSachPhim.get(chiSoPhim % danhSachPhim.size());
                    chiSoPhim++;

                    showtimes.save(Showtime.schedule(
                            movie.getId(), room.getId(), gioTrongNgay,
                            movie.getDurationMin(), 90000L));
                    soSuat++;

                    // Suat tiep theo bat dau ngay sau khi suat nay ket thuc (da gom
                    // ca 15 phut don dep), nen rang buoc no_overlap khong bao gio bi cham.
                    gioTrongNgay = gioTrongNgay
                            .plus(Duration.ofMinutes(movie.getDurationMin()))
                            .plus(Showtime.CLEANING_BUFFER);
                }
            }
        }
        return soSuat;
    }
}
