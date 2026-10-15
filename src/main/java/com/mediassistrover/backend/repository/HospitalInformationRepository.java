package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.HospitalInformation;
import com.mediassistrover.backend.enums.HospitalInformationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface HospitalInformationRepository extends JpaRepository<HospitalInformation, UUID> {
    @Query("""
            select h from HospitalInformation h
            where h.status = :status and h.publicVisible = true
              and (h.effectiveFrom is null or h.effectiveFrom <= :now)
              and (h.effectiveUntil is null or h.effectiveUntil >= :now)
            """)
    Page<HospitalInformation> findPublicEffective(@Param("status") HospitalInformationStatus status,
                                                   @Param("now") Instant now, Pageable pageable);
}
