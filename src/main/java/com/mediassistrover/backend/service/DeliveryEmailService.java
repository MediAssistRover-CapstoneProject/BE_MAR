package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.Alert;
import com.mediassistrover.backend.entity.AlertRecipient;
import com.mediassistrover.backend.enums.AlertChannel;
import com.mediassistrover.backend.enums.AlertDeliveryStatus;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.repository.AlertRecipientRepository;
import com.mediassistrover.backend.repository.JobRepository;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Delivery email is durable and retried independently of job execution. */
@Service
@RequiredArgsConstructor
public class DeliveryEmailService {
    private final JdbcTemplate jdbc;
    private final AlertRecipientRepository recipients;
    private final JobRepository jobs;
    private final DeliveryEmailClaimService claims;
    private final DeliveryEmailGateway gateway;

    @Value("${app.operations.scheduler-enabled:true}") private boolean schedulingEnabled;
    @Value("${app.delivery-email.enabled:false}") private boolean enabled;
    @Value("${app.delivery-email.from:}") private String from;

    @Transactional
    public void enqueue(Alert alert, Account account) {
        if (!enabled || !validAddress(account.getEmail())) return;
        AlertRecipient recipient = recipients.findByAlertAlertIdAndAccountAccountIdAndChannel(
                alert.getAlertId(), account.getAccountId(), AlertChannel.EMAIL).orElseGet(() -> recipients.save(
                AlertRecipient.builder().alert(alert).account(account).channel(AlertChannel.EMAIL)
                        .recipientAddressSnapshot(account.getEmail()).resolvedRoleSnapshot(account.getRole().name())
                        .deliveryStatus(AlertDeliveryStatus.PENDING).createdAt(Instant.now()).build()));
        String jobId = alert.getDataJson().path("jobId").asText();
        if ("DELIVERY_ARRIVED".equals(alert.getNotificationType())) {
            jdbc.update("update delivery_email_outbox set state='EXPIRED' where job_id=? "
                    + "and event_type='DELIVERY_APPROACHING' and state in ('PENDING','SENDING')", jobId);
        }
        jdbc.update("insert ignore into delivery_email_outbox(id,alert_id,recipient_id,recipient_email,job_id,event_type,"
                        + "subject,body,available_at,expires_at) values(?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), alert.getAlertId().toString(), recipient.getAlertRecipientId().toString(),
                account.getEmail(), jobId, alert.getNotificationType(), subject(alert), body(alert), Timestamp.from(Instant.now()),
                Timestamp.from(alert.getCreatedAt().plusSeconds("DELIVERY_CREATED".equals(alert.getNotificationType()) ? 3600 : 120)));
    }

    @Scheduled(fixedDelay = 2000, scheduler = "deliveryEmailScheduler")
    public void sendPending() {
        if (!enabled || !gateway.available() || from == null || from.isBlank() || !schedulingEnabled) return;
        for (int batch = 0; batch < 5; batch++) {
            Map<String, Object> row = claims.claim();
            if (row == null) return;
            String id = row.get("id").toString(), lease = row.get("lease_token").toString();
            int attempts = ((Number) row.get("attempts")).intValue() + 1;
            Instant expiresAt = ((Timestamp) row.get("expires_at")).toInstant();
            var job = jobs.findById(UUID.fromString(row.get("job_id").toString())).orElse(null);
            String eventType = Objects.toString(row.get("event_type"), "");
            if (expiresAt.isBefore(Instant.now()) || job == null || job.getCancelRequestedAt() != null
                    || Set.of(JobStatus.CANCELLED, JobStatus.FAILED).contains(job.getStatus())
                    || (job.getStatus() == JobStatus.SUCCEEDED && "DELIVERY_APPROACHING".equals(eventType))) {
                expire(id, lease, row.get("recipient_id").toString(), "EXPIRED");
                continue;
            }
            try {
                gateway.send(from, row.get("recipient_email").toString(), row.get("subject").toString(), row.get("body").toString());
                jdbc.update("update delivery_email_outbox set state='SENT',attempts=? where id=? and state='SENDING' and lease_token=?",
                        attempts, id, lease);
                recipients.findById(UUID.fromString(row.get("recipient_id").toString())).ifPresent(recipient -> {
                    recipient.setDeliveryStatus(AlertDeliveryStatus.DELIVERED);
                    recipient.setSentAt(Instant.now());
                    recipient.setDeliveredAt(Instant.now());
                    recipients.save(recipient);
                });
            } catch (Exception error) {
                retry(id, lease, attempts, row.get("recipient_id").toString(), error.getClass().getSimpleName());
            }
        }
    }

    private void expire(String id, String lease, String recipientId, String reason) {
        jdbc.update("update delivery_email_outbox set state='EXPIRED',last_error=coalesce(last_error,?) where id=? and state='SENDING' and lease_token=?", reason, id, lease);
        recipients.findById(UUID.fromString(recipientId)).ifPresent(recipient -> {
            recipient.setDeliveryStatus(AlertDeliveryStatus.FAILED);
            if (recipient.getErrorCode() == null || recipient.getErrorCode().isBlank()) recipient.setErrorCode(reason);
            recipients.save(recipient);
        });
    }

    private void retry(String id, String lease, int attempts, String recipientId, String error) {
        String state = attempts >= 6 ? "FAILED" : "PENDING";
        jdbc.update("update delivery_email_outbox set attempts=?,state=?,available_at=?,last_error=? where id=? "
                        + "and state='SENDING' and lease_token=?", attempts, state,
                Timestamp.from(Instant.now().plusSeconds(Math.min(3600, 30L * (1L << attempts)))), error, id, lease);
        if ("FAILED".equals(state)) recipients.findById(UUID.fromString(recipientId)).ifPresent(recipient -> {
            recipient.setDeliveryStatus(AlertDeliveryStatus.FAILED);
            recipient.setErrorCode(error);
            recipients.save(recipient);
        });
    }

    private String subject(Alert alert) { return "MediAssist · " + alert.getNotificationType().replace("DELIVERY_", "Giao hàng: "); }
    private String body(Alert alert) { return alert.getMessage() + "\n\nMở nhiệm vụ: " + alert.getDataJson().path("url").asText(); }
    private boolean validAddress(String email) {
        try { return email != null && !email.isBlank() && InternetAddress.parse(email, true).length == 1; }
        catch (Exception ignored) { return false; }
    }
}
