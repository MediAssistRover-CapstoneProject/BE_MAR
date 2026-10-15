package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.DeliveryRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface DeliveryRequestRepository extends JpaRepository<DeliveryRequest,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from DeliveryRequest d where d.serviceRequestId=:id") Optional<DeliveryRequest> lockById(@Param("id") UUID id);
}
