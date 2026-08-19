package com.cinebook.notification.infra;

import com.cinebook.booking.api.BookingLookup;
import com.cinebook.booking.api.BookingView;
import com.cinebook.identity.api.UserLookup;
import com.cinebook.notification.domain.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gui mot email xac nhan cho moi event — dung mot lan, du event den bao nhieu lan.
 *
 * Kafka giao hang at-least-once, nen day khong phai truong hop hiem: no la binh thuong.
 */
@Component
public class SendBookingConfirmedUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendBookingConfirmedUseCase.class);

    /**
     * Mot cau lenh vua quyet dinh vua ghi. Khong co dong tra ve nghia la event nay da duoc
     * xu ly roi — khong co khe ho giua "kiem tra da gui chua" va "danh dau da gui".
     * Cung mau voi ON CONFLICT ... DO NOTHING ... RETURNING dung cho ghe o Milestone 4.
     */
    private static final String SQL_CLAIM = """
            INSERT INTO notifications (id, event_id, booking_id, recipient, channel, template,
                                       status, created_at)
            VALUES (CAST(:id AS uuid), :eventId, CAST(:bookingId AS uuid), :recipient,
                    'EMAIL', 'BOOKING_CONFIRMED', 'SENT', :createdAt)
            ON CONFLICT (event_id) DO NOTHING
            RETURNING id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final BookingLookup bookingLookup;
    private final UserLookup userLookup;
    private final EmailSender emailSender;

    public SendBookingConfirmedUseCase(NamedParameterJdbcTemplate jdbc, BookingLookup bookingLookup,
                                       UserLookup userLookup, EmailSender emailSender) {
        this.jdbc = jdbc;
        this.bookingLookup = bookingLookup;
        this.userLookup = userLookup;
        this.emailSender = emailSender;
    }

    /**
     * @return true neu email duoc gui trong lan goi nay, false neu event da xu ly truoc do
     */
    @Transactional
    public boolean handle(long eventId, UUID bookingId) {
        Optional<BookingView> booking = bookingLookup.findById(bookingId);
        Optional<UUID> owner = bookingLookup.findOwner(bookingId);
        if (booking.isEmpty() || owner.isEmpty()) {
            // Don khong con ton tai. Khong phai loi he thong va khong co gi de gui.
            log.warn("Bo qua event {}: khong tim thay don {}", eventId, bookingId);
            return false;
        }

        Optional<String> recipient = userLookup.findEmail(owner.get());
        if (recipient.isEmpty()) {
            log.warn("Bo qua event {}: khong tim thay email cua chu don {}", eventId, bookingId);
            return false;
        }

        List<String> daChiem = jdbc.query(SQL_CLAIM, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("eventId", eventId)
                .addValue("bookingId", bookingId.toString())
                .addValue("recipient", recipient.get())
                .addValue("createdAt", Timestamp.from(Instant.now())), (rs, n) -> rs.getString(1));

        if (daChiem.isEmpty()) {
            return false;
        }

        // Gui SAU khi chiem duoc cho trong bang.
        //
        // Danh doi co chu y: neu tien trinh chet ngay sau INSERT, email khong bao gio duoc
        // gui ma DB tin la da gui. Lam nguoc lai (gui truoc, ghi sau) thi rui ro la gui hai
        // lan. Voi email xac nhan, mat mot mail de chiu hon spam khach hai lan — va khach
        // van xem duoc ve trong "don cua toi".
        emailSender.send(recipient.get(), tieuDe(booking.get()), noiDung(booking.get()));
        return true;
    }

    private String tieuDe(BookingView booking) {
        return "Xac nhan dat ve " + booking.code();
    }

    private String noiDung(BookingView booking) {
        return "Phim: " + booking.movieTitle()
                + " | Rap: " + booking.cinemaName()
                + " | Suat: " + booking.startAt()
                + " | Ghe: " + String.join(", ", booking.seats())
                + " | Ma don: " + booking.code();
    }
}
