package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.Floor;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FloorRepository extends JpaRepository<Floor, UUID> {
    boolean existsByHospitalInformationHospitalInformationIdAndFloorCodeIgnoreCase(UUID hospitalId, String floorCode);
    Page<Floor> findByActiveTrue(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Floor f where f.floorId = :id")
    Optional<Floor> findByIdForUpdate(@Param("id") UUID id);
}
