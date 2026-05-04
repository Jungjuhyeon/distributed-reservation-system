package com.jung.reservation.accommodation.infra.persistence;

import com.jung.reservation.accommodation.domain.Accommodation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccommodationJpaRepository extends JpaRepository<Accommodation, Long> {
}
