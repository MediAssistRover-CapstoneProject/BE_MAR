package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.Endpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {
    boolean existsByMapMapIdAndEndpointCodeIgnoreCase(UUID mapId, String endpointCode);
    Page<Endpoint> findByActiveTrueAndPublicVisibleTrue(Pageable pageable);
    List<Endpoint> findByMapMapIdOrderByEndpointCodeAsc(UUID mapId);
}
