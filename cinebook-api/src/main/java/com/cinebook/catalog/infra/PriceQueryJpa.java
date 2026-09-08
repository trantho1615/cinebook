package com.cinebook.catalog.infra;

import com.cinebook.catalog.api.PriceQuery;
import com.cinebook.catalog.domain.showtime.PriceRule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PriceQueryJpa implements PriceQuery {

    private final PriceRuleRepository priceRules;
    private final Counter luotNap;

    /**
     * Nho lai bang gia trong mot khoang NGAN, khong phai mai mai.
     *
     * Lan sua dau tien o Milestone 10 giu cache vinh vien, voi ly do "khong co endpoint nao
     * sua price_rules". Ly do do sai, va PriceTableTest bat duoc ngay:
     * doi_phu_thu_trong_db_thi_lan_doc_ke_tiep_thay_ngay doi phu thu bang SQL TRUC TIEP —
     * dung cach mot nguoi van hanh se lam that. Voi cache vinh vien, ho doi gia roi khong
     * thay gi xay ra cho toi khi khoi dong lai, va he thong ban ve o gia cu suot thoi gian do.
     *
     * TTL bien du lieu cu VO HAN thanh du lieu cu CO GIOI HAN. Doi lai gan het phan tiet
     * kiem: o 1000 req/s, mot lan doc moi 10 giay la 1 tren 10 000 request.
     *
     * Day la mot su NOI LONG co chu y so voi bao dam cu ("lan doc ke tiep thay ngay"), va
     * PriceTableTest da duoc viet lai de noi dung bao dam moi thay vi bi xoa di.
     */
    private final Duration thoiGianSong;
    private volatile BangGia cache;
    private volatile Instant napLuc = Instant.EPOCH;

    public PriceQueryJpa(PriceRuleRepository priceRules, MeterRegistry registry,
                         @Value("${cinebook.catalog.price-cache-ttl:PT10S}") Duration thoiGianSong) {
        this.priceRules = priceRules;
        this.thoiGianSong = thoiGianSong;
        this.luotNap = Counter.builder("cinebook.reference.load")
                .tag("bang", "price_rules")
                .description("So lan doc bang tra cuu tinh tu database")
                .register(registry);
    }

    @Override
    public long priceFor(long basePrice, String seatType) {
        return bangGia().priceFor(basePrice, seatType);
    }

    @Override
    public BangGia bangGia() {
        // Doc mot lan vao bien cuc bo: cache co the bi thay doi giua kiem tra va su dung.
        BangGia hienCo = cache;
        if (hienCo == null || Duration.between(napLuc, Instant.now()).compareTo(thoiGianSong) >= 0) {
            // Hai luong cung nap mot luc la vo hai: chung tao ra hai doi tuong bang nhau,
            // roi mot cai bi bo di. Khoa lai o day chi de doi lay mot lan doc du thua.
            hienCo = new BangGia(surcharges());
            cache = hienCo;
            napLuc = Instant.now();
        }
        return hienCo;
    }

    /**
     * Milestone 8 da bo 96 luot goi xuong con 1 luot moi request. Milestone 10 bo not luot
     * cuoi cung, va ly do khac han:
     *
     *   Thoi gian THUC THI cua cau nay la 0,0045 ms — no chua bao gio ton kem.
     *   Nhung mot vong JDBC toi Postgres ton 0,59 ms, va mot cau `SELECT 1` khong lam gi
     *   ca cung ton 0,66 ms. Cai dat tien la VONG MANG, khong phai truy van.
     *
     * Ghi chu cu o day noi "toi uu khi da do, khong toi uu vi linh cam". Da do, va so lieu
     * chi ve mot cho khac voi cho ai cung nhin vao.
     */
    private Map<String, Long> surcharges() {
        luotNap.increment();
        return priceRules.findAll().stream()
                .collect(Collectors.toMap(
                        PriceRule::getSeatType,
                        PriceRule::getSurcharge,
                        (a, b) -> a));
    }
}
