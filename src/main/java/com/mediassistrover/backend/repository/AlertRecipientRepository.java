package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.AlertRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AlertRecipientRepository extends JpaRepository<AlertRecipient,UUID> {
    List<AlertRecipient> findByAlertAlertIdOrderByCreatedAtAsc(UUID alertId);
    Optional<AlertRecipient> findByAlertAlertIdAndAccountAccountId(UUID alertId,UUID accountId);
    long countByAlertAlertId(UUID alertId);
    long countByAlertAlertIdAndAcknowledgedAtIsNotNull(UUID alertId);
}
