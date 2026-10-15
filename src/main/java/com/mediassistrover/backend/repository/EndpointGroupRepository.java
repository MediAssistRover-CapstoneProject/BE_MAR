package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.EndpointGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EndpointGroupRepository extends JpaRepository<EndpointGroup, UUID> {
    boolean existsByMapMapIdAndGroupCodeIgnoreCase(UUID mapId, String groupCode);
}
