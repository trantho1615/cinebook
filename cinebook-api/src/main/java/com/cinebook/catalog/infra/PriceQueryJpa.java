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
        return basePrice + surcharges().getOrDefault(seatType, 0L);
    }

    /**
     * Bang gia chi co ba dong va gan nhu khong bao gio doi, nhung van doc tu database
     * moi lan goi thay vi cache: o milestone nay chua co so lieu nao cho thay day la
     * diem nghen. Toi uu khi da do, khong toi uu vi linh cam.
     */
    private Map<String, Long> surcharges() {
        return priceRules.findAll().stream()
                .collect(Collectors.toMap(
                        PriceRule::getSeatType,
                        PriceRule::getSurcharge,
                        (a, b) -> a));
    }
}
