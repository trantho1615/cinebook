package com.cinebook.catalog.infra;

import com.cinebook.catalog.domain.venue.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByRoomIdOrderByRowLabelAscSeatNumberAsc(UUID roomId);

    long countByRoomId(UUID roomId);
}
