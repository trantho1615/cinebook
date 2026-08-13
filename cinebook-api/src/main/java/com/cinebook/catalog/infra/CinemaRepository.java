package com.cinebook.catalog.infra;

import com.cinebook.catalog.domain.venue.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CinemaRepository extends JpaRepository<Cinema, UUID> {

    List<Cinema> findByCityAndDistrictOrderByNameAsc(String city, String district);

    List<Cinema> findAllByOrderByNameAsc();
}
