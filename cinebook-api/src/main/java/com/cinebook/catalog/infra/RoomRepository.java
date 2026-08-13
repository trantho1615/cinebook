package com.cinebook.catalog.infra;

import com.cinebook.catalog.domain.venue.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByCinemaIdOrderByNameAsc(UUID cinemaId);

    boolean existsByCinemaIdAndName(UUID cinemaId, String name);
}
