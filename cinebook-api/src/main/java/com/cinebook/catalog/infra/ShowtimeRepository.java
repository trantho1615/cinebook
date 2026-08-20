package com.cinebook.catalog.infra;

import com.cinebook.catalog.domain.showtime.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ShowtimeRepository extends JpaRepository<Showtime, UUID> {

    long countByStartAtAfter(Instant moc);

    /**
     * Phong nao da co lich chieu sau moc nay. DemoDataSeeder dung de bo qua chung khi nap
     * bu lich: chen de len se cham rang buoc no_overlap, va vi seeder chay luc khoi dong
     * nen exception do lam ca ung dung khong len duoc.
     */
    @Query("SELECT DISTINCT s.roomId FROM Showtime s WHERE s.startAt > :moc")
    List<UUID> findRoomIdsWithShowtimeAfter(@Param("moc") Instant moc);
}
