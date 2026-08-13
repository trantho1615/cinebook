package com.cinebook.catalog.infra;

import com.cinebook.catalog.domain.showtime.PriceRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRuleRepository extends JpaRepository<PriceRule, String> {
}
