package com.cinebook.catalog.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Be mat cong khai cua module catalog cho phia doc suat chieu.
 * Module khac (booking) chi duoc phep phu thuoc vao interface nay, khong duoc cham
 * vao catalog.domain hay catalog.infra — luat do do ModuleBoundaryTest ep.
 */
public interface ShowtimeQuery {

    Optional<ShowtimeDetail> findDetail(UUID showtimeId);
}
