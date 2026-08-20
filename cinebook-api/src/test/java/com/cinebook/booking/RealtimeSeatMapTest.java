package com.cinebook.booking;

import com.cinebook.booking.infra.SweepExpiredHoldsUseCase;
import com.cinebook.shared.realtime.SeatMapChannel;
import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeSeatMapTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    SweepExpiredHoldsUseCase sweeper;

    @Autowired
    SeatMapChannel seatMapChannel;

    @Autowired
    PlatformTransactionManager txManager;

    private BookingFixture fixture;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
    }

    @Test
    void giu_ghe_thi_client_dang_mo_so_do_ghe_nhan_duoc_ngay() throws Exception {
        BlockingQueue<String> nhanDuoc = dangKyNhanTin(fixture.showtimeId());

        hold(fixture.tokenA(), fixture.seatIds("A1", "A2"));

        String banTin = nhanDuoc.poll(10, TimeUnit.SECONDS);
        assertThat(banTin).isNotNull()
                .contains(fixture.showtimeId())
                .contains("HELD")
                .contains("A1")
                .contains("A2");
    }

    /**
     * Sweeper chay trong cinebook-worker, mot tien trinh khac. No van bao duoc cho client
     * dang noi voi cinebook-api — vi ca hai gap nhau o Redis pub/sub chu khong o bo nho
     * cua mot tien trinh nao.
     */
    @Test
    void sweeper_nha_ghe_thi_client_cung_nhan_duoc() throws Exception {
        hold(fixture.tokenA(), fixture.seatIds("A1"));
        lamChoHetHan();

        BlockingQueue<String> nhanDuoc = dangKyNhanTin(fixture.showtimeId());
        sweeper.sweep();

        String banTin = nhanDuoc.poll(10, TimeUnit.SECONDS);
        assertThat(banTin).isNotNull()
                .contains("RELEASED")
                .contains("A1");
    }

    /**
     * Ly do SeatMapChannel doi den afterCommit: khong bao gio phat di mot su that bi
     * rollback, vi da phat roi thi khong co cach nao rut lai.
     *
     * Test nay goi thang vao kenh trong mot transaction roi nem loi, thay vi di vong qua
     * duong giu ghe. Ly do: SeatsUnavailableException duoc nem TRUOC khi hold() cham toi
     * lenh publish, nen nhanh do khong phan biet duoc gi — da thu va thay ca bon test van
     * xanh khi go afterCommit ra.
     */
    @Test
    void transaction_bi_rollback_thi_khong_bao_gi() throws Exception {
        UUID showtimeId = UUID.fromString(fixture.showtimeId());
        BlockingQueue<String> nhanDuoc = dangKyNhanTin(fixture.showtimeId());
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            seatMapChannel.seatsChanged(showtimeId, "HELD", List.of("A1"));
            throw new IllegalStateException("su co giua chung");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(nhanDuoc.poll(3, TimeUnit.SECONDS))
                .as("khong duoc bao ve mot thay doi da bi rollback")
                .isNull();

        // Va van bao binh thuong khi transaction commit — de test nay khong xanh chi vi
        // kenh hong hoan toan.
        tx.executeWithoutResult(status ->
                seatMapChannel.seatsChanged(showtimeId, "HELD", List.of("A2")));
        assertThat(nhanDuoc.poll(10, TimeUnit.SECONDS)).contains("A2");
    }

    /**
     * Spec muc 7: "Redis chet -> seat map fallback doc thang Postgres, cham hon nhung van
     * ban duoc ve".
     *
     * Kiem chung o dung cho co the lam hong: SeatMapChannel la thanh phan duy nhat trong
     * duong giu ghe cham toi Redis. Cam vao mot Redis khong ton tai va khang dinh no
     * khong nem gi ra ngoai.
     */
    @Test
    void redis_chet_thi_duong_giu_ghe_khong_bi_keo_theo() {
        RedisConnectionFactory chet = new LettuceConnectionFactory("localhost", 6399);
        ((LettuceConnectionFactory) chet).afterPropertiesSet();
        SeatMapChannel kenhHong = new SeatMapChannel(new StringRedisTemplate(chet));

        assertThatCode(() ->
                kenhHong.seatsChanged(UUID.randomUUID(), "HELD", List.of("A1")))
                .doesNotThrowAnyException();
    }

    private BlockingQueue<String> dangKyNhanTin(String showtimeId) throws Exception {
        BlockingQueue<String> nhanDuoc = new LinkedBlockingQueue<>();

        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        StompSession session = stomp
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
                })
                .get(10, TimeUnit.SECONDS);

        session.subscribe("/topic/showtimes/" + showtimeId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                nhanDuoc.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        return nhanDuoc;
    }

    private void lamChoHetHan() {
        db.update("UPDATE seat_hold SET expires_at = now() - interval '1 minute' WHERE status = 'HELD'");
        db.update("UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE status = 'PENDING'");
    }

    /**
     * Tra ve null khi giu ghe that bai (409) — test rollback co y goi mot lan that bai.
     */
    private String hold(String token, List<String> seatIds) {
        JsonNode body = client().post()
                .uri("/showtimes/" + fixture.showtimeId() + "/holds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("seatIds", seatIds))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();
        JsonNode bookingId = body == null ? null : body.get("bookingId");
        return bookingId == null ? null : bookingId.asText();
    }
}
