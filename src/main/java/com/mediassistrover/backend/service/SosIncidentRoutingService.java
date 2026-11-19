package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.emergency.response.EmergencyResponses.IncidentResponse;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.entity.EmergencyIncident.SosRoutingStatus;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.media.MediaSessionService;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rechecks only unclaimed incidents; existing responders and acknowledgements are preserved. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SosIncidentRoutingService {
    private final EmergencyIncidentRepository incidents;
    private final SosResponderRouting routing;
    private final StaffOperationalScopePolicy scope;
    private final EmergencyService emergencies;
    private final AlertRepository alerts;
    private final AlertRecipientRepository recipients;
    private final ServiceRequestRepository requests;
    private final JobRepository jobs;
    private final MediaSessionService media;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    public record Eligibility(SosRoutingStatus status, String reason, UUID roomId, String roomName,
                              UUID candidateAccountId, Instant checkedAt) { }
    public record Response(IncidentResponse incident, Eligibility eligibility, boolean canRetry) { }

    public Response inspect(UUID id, AccountPrincipal actor) {
        requireAdmin(actor);
        var incident = incidents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        if (incident.getArchivedAt() != null) throw new ResourceNotFoundException("Incident not found");
        var now = clock.instant();
        return response(incident, actor, routing.resolve(incident.getMap(), now, null), now);
    }

    @Transactional
    public Response retry(UUID id, long expectedVersion, AccountPrincipal actor, RequestMetadata metadata) {
        Account admin = requireAdmin(actor);
        var incident = incidents.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        if (incident.getArchivedAt() != null || !incident.supportsResponderConfirmation() || !incident.isRoutedSos()
                || incident.getStatus() != EmergencyIncidentStatus.OPEN || incident.getAcknowledgedByAccount() != null)
            throw new ConflictException("Chỉ điều phối lại sự cố đang chờ và chưa có người tiếp nhận.");
        var now = clock.instant();
        // Repeated requests after a successful assignment cannot reopen media or duplicate alerts.
        if (incident.getSosResponder() != null)
            return response(incident, actor, routing.resolve(incident.getMap(), now, null), now);
        if (incident.getRowVersion() != expectedVersion)
            throw new ConflictException("Sự cố đã thay đổi. Hãy tải lại trước khi điều phối.");
        var assignment = routing.resolve(incident.getMap(), now, null);
        var previousStatus = incident.getSosRoutingStatus();
        incident.setSosRoutingStatus(assignment.status());
        incident.setSosResponder(assignment.responder());
        if (assignment.responder() != null) {
            notifyResponder(incident, assignment.responder(), now);
            UUID sessionId = media.openSos(incident);
            preparePendingRequest(incident, sessionId);
        }
        audit.record(admin, "SOS_ROUTING_RETRIED", "EmergencyIncident", id,
                Map.of("sosRoutingStatus", previousStatus.name()),
                Map.of("sosRoutingStatus", assignment.status().name(), "reason", assignment.reason()), metadata);
        incidents.flush();
        return response(incident, actor, assignment, now);
    }

    private void notifyResponder(EmergencyIncident incident, Account responder, Instant now) {
        for (var found : alerts.findByIncidentIncidentId(incident.getIncidentId())) {
            var alert = alerts.lockById(found.getAlertId()).orElseThrow();
            if (alert.getStatus() != AlertStatus.OPEN || !"SOS".equals(alert.getNotificationType())) continue;
            alert.setRequestedRecipientsJson(mapper.valueToTree(List.of(responder.getAccountId())));
            ObjectNode data = alert.getDataJson() instanceof ObjectNode object ? object.deepCopy() : mapper.createObjectNode();
            data.put("sos_routing_status", "ASSIGNED");
            alert.setDataJson(data);
            alert.setExpiresAt(now.plusSeconds(300));
            if (!recipients.existsByAlertAlertIdAndAccountAccountId(alert.getAlertId(), responder.getAccountId()))
                recipients.save(AlertRecipient.builder().alert(alert).account(responder).channel(AlertChannel.WEB)
                        .recipientAddressSnapshot(responder.getUsername()).resolvedRoleSnapshot(responder.getRole().name())
                        .deliveryStatus(AlertDeliveryStatus.PENDING).createdAt(now).build());
        }
    }

    private void preparePendingRequest(EmergencyIncident incident, UUID sessionId) {
        requests.findByRelatedIncidentId(incident.getIncidentId()).ifPresent(found -> {
            var request = requests.lockById(found.getServiceRequestId()).orElseThrow();
            if (request.getArchivedAt() != null || request.getStatus() != ServiceRequestStatus.SUBMITTED
                    || request.getRequestType() != ServiceRequestType.EMERGENCY_SOS) return;
            ObjectNode data = request.getRequestDataJson() instanceof ObjectNode object ? object.deepCopy() : mapper.createObjectNode();
            if (sessionId != null) data.put("media_session_id", sessionId.toString());
            if (!incident.isSuspectedFall() && !data.path("staff_review_required").asBoolean()
                    && jobs.findByServiceRequestServiceRequestId(request.getServiceRequestId()).isEmpty())
                data.put("dispatch_pending", true);
            request.setRequestDataJson(data);
        });
    }

    private Account requireAdmin(AccountPrincipal actor) {
        Account account = scope.requireCurrentStaffOrAdmin(actor);
        if (account.getRole() != Role.ADMIN) throw new AccessDeniedException("Only ADMIN can retry SOS routing");
        return account;
    }

    private Response response(EmergencyIncident incident, AccountPrincipal actor, SosResponderRouting.Assignment assignment, Instant now) {
        return new Response(emergencies.get(incident.getIncidentId(), false, actor),
                new Eligibility(assignment.status(), assignment.reason(), assignment.roomId(), assignment.roomName(),
                        assignment.responder() == null ? null : assignment.responder().getAccountId(), now),
                incident.getArchivedAt() == null && incident.supportsResponderConfirmation() && incident.isRoutedSos()
                        && incident.getStatus() == EmergencyIncidentStatus.OPEN && incident.getSosResponder() == null
                        && incident.getAcknowledgedByAccount() == null);
    }
}
