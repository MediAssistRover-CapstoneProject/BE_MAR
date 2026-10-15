package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.EmergencyIncident;
import com.mediassistrover.backend.enums.EmergencyIncidentStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
public interface EmergencyIncidentRepository extends JpaRepository<EmergencyIncident,UUID> {
    Page<EmergencyIncident> findByStatus(EmergencyIncidentStatus status,Pageable pageable);
    Optional<EmergencyIncident> findByDetectionEventDetectionEventId(UUID detectionEventId);
    long countByStatusIn(Collection<EmergencyIncidentStatus> statuses);
}
