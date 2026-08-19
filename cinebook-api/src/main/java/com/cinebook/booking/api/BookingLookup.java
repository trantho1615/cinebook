package com.cinebook.booking.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Be mat doc cua module booking cho module khac.
 *
 * payment can biet don thuoc ve ai va o trang thai nao, nhung khong duoc cham vao
 * BookingQueryJdbc trong booking.infra — ModuleBoundaryTest chan dieu do.
 */
public interface BookingLookup {

    Optional<UUID> findOwner(UUID bookingId);

    Optional<BookingView> findById(UUID bookingId);
}
