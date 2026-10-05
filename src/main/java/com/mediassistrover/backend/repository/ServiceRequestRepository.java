package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.ServiceRequest;
import com.mediassistrover.backend.enums.ServiceRequestStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest,UUID> {
    Optional<ServiceRequest> findByIdempotencyKey(String key);
    Page<ServiceRequest> findByStatus(ServiceRequestStatus status,Pageable pageable);
    Page<ServiceRequest> findByRequestedByAccountAccountId(UUID accountId,Pageable pageable);
    Page<ServiceRequest> findByRequestedByAccountAccountIdAndStatus(UUID accountId,ServiceRequestStatus status,Pageable pageable);
}
