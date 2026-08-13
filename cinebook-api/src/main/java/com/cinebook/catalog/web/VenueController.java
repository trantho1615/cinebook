package com.cinebook.catalog.web;

import com.cinebook.catalog.domain.venue.Cinema;
import com.cinebook.catalog.domain.venue.CinemaNotFoundException;
import com.cinebook.catalog.domain.venue.Room;
import com.cinebook.catalog.domain.venue.Seat;
import com.cinebook.catalog.domain.venue.SeatLayout;
import com.cinebook.catalog.infra.CinemaRepository;
import com.cinebook.catalog.infra.RoomRepository;
import com.cinebook.catalog.infra.SeatRepository;
import com.cinebook.catalog.web.dto.CinemaResponse;
import com.cinebook.catalog.web.dto.CreateCinemaRequest;
import com.cinebook.catalog.web.dto.CreateRoomRequest;
import com.cinebook.catalog.web.dto.RoomResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class VenueController {

    private final CinemaRepository cinemas;
    private final RoomRepository rooms;
    private final SeatRepository seats;

    public VenueController(CinemaRepository cinemas, RoomRepository rooms, SeatRepository seats) {
        this.cinemas = cinemas;
        this.rooms = rooms;
        this.seats = seats;
    }

    @GetMapping("/cinemas")
    public List<CinemaResponse> listCinemas(@RequestParam(required = false) String city,
                                            @RequestParam(required = false) String district) {
        List<Cinema> found = (city == null || district == null)
                ? cinemas.findAllByOrderByNameAsc()
                : cinemas.findByCityAndDistrictOrderByNameAsc(city, district);
        return found.stream().map(CinemaResponse::from).toList();
    }

    @GetMapping("/cinemas/{cinemaId}/rooms")
    public List<RoomResponse> listRooms(@PathVariable UUID cinemaId) {
        return rooms.findByCinemaIdOrderByNameAsc(cinemaId).stream()
                .map(room -> RoomResponse.from(room, seats.countByRoomId(room.getId())))
                .toList();
    }

    @PostMapping("/admin/cinemas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CinemaResponse createCinema(@Valid @RequestBody CreateCinemaRequest request) {
        Cinema cinema = Cinema.create(
                request.name(), request.address(), request.district(), request.city());
        return CinemaResponse.from(cinemas.save(cinema));
    }

    /**
     * Tao phong va sinh toan bo ghe trong CUNG mot transaction: mot phong khong co ghe
     * la trang thai vo nghia, khong duoc phep ton tai du chi trong choc lat.
     */
    @PostMapping("/admin/cinemas/{cinemaId}/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RoomResponse createRoom(@PathVariable UUID cinemaId,
                                   @Valid @RequestBody CreateRoomRequest request) {
        if (!cinemas.existsById(cinemaId)) {
            throw new CinemaNotFoundException(cinemaId);
        }

        Room room = rooms.saveAndFlush(Room.create(cinemaId, request.name(), request.roomType()));

        List<Seat> generated = SeatLayout.generate(request.rowCount(), request.seatsPerRow())
                .stream()
                .map(p -> Seat.create(room.getId(), p.rowLabel(), p.seatNumber(), p.type()))
                .toList();
        seats.saveAll(generated);

        return RoomResponse.from(room, generated.size());
    }
}
