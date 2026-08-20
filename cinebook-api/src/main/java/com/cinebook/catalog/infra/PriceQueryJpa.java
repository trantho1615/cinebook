package com.cinebook.catalog.infra;

import com.cinebook.catalog.api.PriceQuery;
import com.cinebook.catalog.domain.showtime.PriceRule;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PriceQueryJpa implements PriceQuery {

    private final PriceRuleRepository priceRules;

    public PriceQueryJpa(PriceRuleRepository priceRules) {
        this.priceRules = priceRules;
    }

    @Override
    public long priceFor(long basePrice, String seatType) {
        return bangGia().priceFor(basePrice, seatType);
    }

    @Override
    public BangGia bangGia() {
        return new BangGia(surcharges());
    }

    /**
     * Van doc tu database moi lan goi, KHONG cache.
     *
     * Ghi chu cu o day noi "toi uu khi da do, khong toi uu vi linh cam". Da do o Milestone 8:
     * van de khong phai la doc bang nay ton kem, ma la ShowtimeQueryJdbc goi no 96 lan cho
     * mot so do ghe. Cach sua dung la doc mot lan roi dung lai (xem PriceQuery.bangGia),
     * chu khong phai them mot lop cache co the tra gia cu.
     */
    private Map<String, Long> surcharges() {
        return priceRules.findAll().stream()
                .collect(Collectors.toMap(
                        PriceRule::getSeatType,
                        PriceRule::getSurcharge,
                        (a, b) -> a));
    }
}
