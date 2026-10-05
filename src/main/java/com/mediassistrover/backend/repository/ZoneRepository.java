package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.Zone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    boolean existsByMapMapIdAndZoneCodeIgnoreCase(UUID mapId, String zoneCode);
    Page<Zone> findByActiveTrueAndPublicVisibleTrue(Pageable pageable);
}
