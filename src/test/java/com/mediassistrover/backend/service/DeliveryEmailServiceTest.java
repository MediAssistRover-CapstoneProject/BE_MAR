package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.Alert;
import com.mediassistrover.backend.entity.AlertRecipient;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.enums.AlertChannel;
import com.mediassistrover.backend.enums.AlertDeliveryStatus;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.enums.Role;
import com.mediassistrover.backend.repository.AlertRecipientRepository;
import com.mediassistrover.backend.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryEmailServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AlertRecipientRepository recipients = mock(AlertRecipientRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final DeliveryEmailClaimService claims = mock(DeliveryEmailClaimService.class);
    private final DeliveryEmailGateway gateway = mock(DeliveryEmailGateway.class);
    private final DeliveryEmailService service = new DeliveryEmailService(jdbc, recipients, jobs, claims, gateway);
    private final UUID alertId = UUID.randomUUID(), accountId = UUID.randomUUID(), recipientId = UUID.randomUUID(), jobId = UUID.randomUUID();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "schedulingEnabled", true);
        ReflectionTestUtils.setField(service, "from", "robot@example.test");
        when(gateway.available()).thenReturn(true);
    }

    @Test void queuesEmailOnlyForValidRecipient() {
        var account = Account.builder().accountId(accountId).email("receiver@example.test").role(Role.STAFF).build();
        var alert = alert("DELIVERY_CREATED");
        var recipient = AlertRecipient.builder().alertRecipientId(recipientId).build();
        when(recipients.findByAlertAlertIdAndAccountAccountIdAndChannel(alertId, accountId, AlertChannel.EMAIL)).thenReturn(Optional.empty());
        when(recipients.save(any())).thenReturn(recipient);

        service.enqueue(alert, account);

        verify(jdbc).update(startsWith("insert ignore into delivery_email_outbox"), any(Object[].class));
        verify(recipients).save(argThat(r -> r.getChannel() == AlertChannel.EMAIL
                && r.getDeliveryStatus() == AlertDeliveryStatus.PENDING));
    }

    @Test void ignoresMissingAddress() {
        service.enqueue(alert("DELIVERY_CREATED"), Account.builder().accountId(accountId).role(Role.STAFF).build());
        verifyNoInteractions(jdbc, recipients);
    }

    @Test void queuesEmailWhenSmtpIsNotConfiguredForLaterRetry() {
        when(gateway.available()).thenReturn(false);
        var account = Account.builder().accountId(accountId).email("receiver@example.test").role(Role.STAFF).build();
        var alert = alert("DELIVERY_CREATED");
        var recipient = AlertRecipient.builder().alertRecipientId(recipientId).build();
        when(recipients.findByAlertAlertIdAndAccountAccountIdAndChannel(alertId, accountId, AlertChannel.EMAIL)).thenReturn(Optional.empty());
        when(recipients.save(any())).thenReturn(recipient);

        service.enqueue(alert, account);
        service.sendPending();

        verify(jdbc).update(startsWith("insert ignore into delivery_email_outbox"), any(Object[].class));
        verifyNoInteractions(claims);
    }

    @Test void deliversClaimedEmailAndRecordsRecipientStatus() throws Exception {
        var recipient = AlertRecipient.builder().alertRecipientId(recipientId).deliveryStatus(AlertDeliveryStatus.PENDING).build();
        claimOneRow();
        when(jobs.findById(jobId)).thenReturn(Optional.of(Job.builder().jobId(jobId).status(JobStatus.RUNNING).build()));
        when(recipients.findById(recipientId)).thenReturn(Optional.of(recipient));

        service.sendPending();

        verify(gateway).send("robot@example.test", "receiver@example.test", "Giao hàng", "Mở nhiệm vụ: /jobs/1");
        verify(jdbc).update(startsWith("update delivery_email_outbox set state='SENT'"), any(), any(), any());
        verify(recipients).save(argThat(r -> r.getDeliveryStatus() == AlertDeliveryStatus.DELIVERED));
    }

    @Test void retriesTemporarySmtpFailure() throws Exception {
        claimOneRow();
        when(jobs.findById(jobId)).thenReturn(Optional.of(Job.builder().jobId(jobId).status(JobStatus.RUNNING).build()));
        doThrow(new IllegalStateException("temporary")).when(gateway).send(anyString(), anyString(), anyString(), anyString());

        service.sendPending();

        verify(jdbc).update(startsWith("update delivery_email_outbox set attempts="), any(), eq("PENDING"), any(), eq("IllegalStateException"), any(), any());
    }

    private Alert alert(String type) {
        return Alert.builder().alertId(alertId).notificationType(type).message("Mở nhiệm vụ").createdAt(Instant.now())
                .dataJson(new ObjectMapper().createObjectNode().put("jobId", jobId.toString()).put("url", "/jobs/1")).build();
    }

    private Map<String, Object> row() {
        return Map.of("id", UUID.randomUUID().toString(), "lease_token", UUID.randomUUID().toString(), "attempts", 0,
                "expires_at", Timestamp.from(Instant.now().plusSeconds(60)), "job_id", jobId.toString(),
                "recipient_id", recipientId.toString(), "recipient_email", "receiver@example.test", "event_type", "DELIVERY_CREATED",
                "subject", "Giao hàng", "body", "Mở nhiệm vụ: /jobs/1");
    }

    private void claimOneRow() {
        var first = new AtomicBoolean(true);
        when(claims.claim()).thenAnswer(invocation -> first.getAndSet(false) ? row() : null);
    }
}
