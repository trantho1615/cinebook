package com.cinebook.booking.web;

import com.cinebook.booking.api.SeatMapEntry;
import com.cinebook.booking.domain.AdjacentSeatFinder;
import com.cinebook.booking.domain.NoAdjacentSeatsException;
import com.cinebook.booking.domain.ShowtimeUnknownException;
import com.cinebook.booking.infra.SeatMapQueryJdbc;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
public class SeatMapController {

    private final SeatMapQueryJdbc seatMapQuery;

    public SeatMapController(SeatMapQueryJdbc seatMapQuery) {
        this.seatMapQuery = seatMapQuery;
    }

    @GetMapping("/showtimes/{showtimeId}/seats")
    public List<SeatMapEntry> seatMap(@PathVariable UUID showtimeId) {
        return seatMapQuery.seatMap(showtimeId)
                .orElseThrow(() -> new ShowtimeUnknownException(showtimeId));
    }

    /**
     * Seam so 2 o spec muc 11: cau "con 2 ghe trong canh nhau o hang giua" ma
     * AI Agent nghe duoc dich thang ra loi goi nay. UI cung dung no cho nut
     * goi y ghe dep.
     */
    @GetMapping("/showtimes/{showtimeId}/seats/suggest")
    public List<SeatMapEntry> suggest(@PathVariable UUID showtimeId,
                                      @RequestParam(defaultValue = "2") @Min(1) @Max(8) int count) {
        List<SeatMapEntry> seatMap = seatMapQuery.seatMap(showtimeId)
                .orElseThrow(() -> new ShowtimeUnknownException(showtimeId));

        return AdjacentSeatFinder.findBest(seatMap, count)
                .orElseThrow(() -> new NoAdjacentSeatsException(count));
    }
}
