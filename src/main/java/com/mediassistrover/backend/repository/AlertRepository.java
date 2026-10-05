package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.Alert;
import com.mediassistrover.backend.enums.AlertStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AlertRepository extends JpaRepository<Alert,UUID> {
    Optional<Alert> findByOperationKey(String operationKey);
    Page<Alert> findByStatus(AlertStatus status,Pageable pageable);
    List<Alert> findByIncidentIncidentId(UUID incidentId);
}
