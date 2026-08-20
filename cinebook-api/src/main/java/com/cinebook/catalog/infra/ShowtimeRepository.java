package com.cinebook.catalog.infra;

import com.cinebook.catalog.domain.showtime.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ShowtimeRepository extends JpaRepository<Showtime, UUID> {

    long countByStartAtAfter(Instant moc);
}
