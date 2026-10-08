package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.ServiceRequest;
import com.mediassistrover.backend.enums.ServiceRequestStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.mediassistrover.backend.enums.ServiceRequestType;
import java.util.*;
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest,UUID> {
    interface TypeCount { ServiceRequestType getRequestType(); long getTotal(); }
    Optional<ServiceRequest> findByIdempotencyKey(String key);
    Optional<ServiceRequest> findByRelatedIncidentId(UUID incidentId);
    Page<ServiceRequest> findByStatus(ServiceRequestStatus status,Pageable pageable);
    Page<ServiceRequest> findByRequestedByAccountAccountId(UUID accountId,Pageable pageable);
    Page<ServiceRequest> findByRequestedByAccountAccountIdAndStatus(UUID accountId,ServiceRequestStatus status,Pageable pageable);
    @Query("select r.requestType as requestType, count(r) as total from ServiceRequest r group by r.requestType")
    List<TypeCount> countGroupedByType();
}
