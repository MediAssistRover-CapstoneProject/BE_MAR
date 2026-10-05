package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.DeliveryRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface DeliveryRequestRepository extends JpaRepository<DeliveryRequest,UUID> { }
