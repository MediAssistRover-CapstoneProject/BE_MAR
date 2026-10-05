package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import com.mediassistrover.backend.dto.alert.response.AlertResponses.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AlertServiceImpl implements AlertService {
    private final AlertRepository alertRepository; private final AlertRecipientRepository recipientRepository;
    private final AccountRepository accountRepository; private final AuditService auditService;
    private final ObjectMapper objectMapper; private final Clock clock;

    @Override @Transactional public Alert createSystemAlert(String key,String type,AlertSeverity severity,String message,JsonNode data,EmergencyIncident incident,ServiceRequest request,JobStep step,Zone zone,boolean requireAck,AlertAckPolicy policy,Instant expiresAt){
        Alert found=alertRepository.findByOperationKey(key).orElse(null);if(found!=null)return found;Instant now=Instant.now(clock);
        List<Account> recipients=accountRepository.findByStatusAndRoleInOrderByAccountCodeAsc(AccountStatus.ACTIVE,List.of(Role.ADMIN,Role.STAFF));
        Alert alert=alertRepository.saveAndFlush(Alert.builder().incident(incident).jobStep(step).serviceRequest(request).zone(zone).notificationType(type).severity(severity).status(AlertStatus.OPEN).message(message).dataJson(data==null?objectMapper.createObjectNode():data.deepCopy()).requestedRecipientsJson(objectMapper.valueToTree(recipients.stream().map(Account::getAccountId).toList())).requireAck(requireAck).ackPolicy(policy).operationKey(key).createdAt(now).expiresAt(expiresAt).build());
        for(Account account:recipients)recipientRepository.save(AlertRecipient.builder().alert(alert).account(account).channel(AlertChannel.WEB).recipientAddressSnapshot(account.getUsername()).resolvedRoleSnapshot(account.getRole().name()).deliveryStatus(AlertDeliveryStatus.DELIVERED).sentAt(now).deliveredAt(now).createdAt(now).build());
        return alert;
    }
    @Override public AlertResponse get(UUID id,AccountPrincipal actor){Alert alert=alert(id);requireRecipientOrAdmin(alert,actor);return response(alert);}
    @Override public Page<AlertResponse> list(AlertStatus status,AccountPrincipal actor,Pageable pageable){Page<Alert> page=status==null?alertRepository.findAll(pageable):alertRepository.findByStatus(status,pageable);return page.map(this::response);}
    @Override @Transactional public AlertResponse acknowledge(UUID id,AccountPrincipal actor,RequestMetadata md){Alert alert=alert(id);if(alert.getStatus()==AlertStatus.RESOLVED||alert.getStatus()==AlertStatus.CLOSED)throw new BusinessRuleException("Resolved or closed Alert cannot be acknowledged");AlertRecipient recipient=recipientRepository.findByAlertAlertIdAndAccountAccountId(id,actor.accountId()).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Alert is not assigned to this account"));if(recipient.getAcknowledgedAt()==null)recipient.setAcknowledgedAt(Instant.now(clock));long acked=recipientRepository.countByAlertAlertIdAndAcknowledgedAtIsNotNull(id)+0;long total=recipientRepository.countByAlertAlertId(id);if(alert.getAckPolicy()==AlertAckPolicy.ANY||acked>=total)alert.setStatus(AlertStatus.ACKNOWLEDGED);audit(actor,"ALERT_ACKNOWLEDGED",alert,md);return response(alert);}
    @Override @Transactional public AlertResponse resolve(UUID id,AccountPrincipal actor,RequestMetadata md){Alert alert=alert(id);requireRecipientOrAdmin(alert,actor);if(alert.getStatus()!=AlertStatus.ACKNOWLEDGED)throw new BusinessRuleException("Only ACKNOWLEDGED Alert can be resolved");alert.setStatus(AlertStatus.RESOLVED);audit(actor,"ALERT_RESOLVED",alert,md);return response(alert);}
    @Override @Transactional public AlertResponse close(UUID id,AccountPrincipal actor,RequestMetadata md){Alert alert=alert(id);requireRecipientOrAdmin(alert,actor);if(alert.getStatus()!=AlertStatus.RESOLVED)throw new BusinessRuleException("Only RESOLVED Alert can be closed");alert.setStatus(AlertStatus.CLOSED);audit(actor,"ALERT_CLOSED",alert,md);return response(alert);}
    private AlertResponse response(Alert a){List<RecipientResponse> recipients=recipientRepository.findByAlertAlertIdOrderByCreatedAtAsc(a.getAlertId()).stream().map(r->new RecipientResponse(r.getAlertRecipientId(),r.getAccount().getAccountId(),r.getChannel(),r.getDeliveryStatus(),r.getSentAt(),r.getDeliveredAt(),r.getAcknowledgedAt(),r.getErrorCode())).toList();return new AlertResponse(a.getAlertId(),a.getIncident()==null?null:a.getIncident().getIncidentId(),a.getJobStep()==null?null:a.getJobStep().getJobStepId(),a.getServiceRequest()==null?null:a.getServiceRequest().getServiceRequestId(),a.getZone()==null?null:a.getZone().getZoneId(),a.getNotificationType(),a.getSeverity(),a.getStatus(),a.getMessage(),a.getDataJson(),a.isRequireAck(),a.getAckPolicy(),a.getCreatedAt(),a.getExpiresAt(),a.getRowVersion(),recipients);}
    private void requireRecipientOrAdmin(Alert alert,AccountPrincipal actor){if(actor.role()==Role.ADMIN)return;if(recipientRepository.findByAlertAlertIdAndAccountAccountId(alert.getAlertId(),actor.accountId()).isEmpty())throw new org.springframework.security.access.AccessDeniedException("Alert is outside your scope");}
    private void audit(AccountPrincipal p,String action,Alert a,RequestMetadata md){Account actor=accountRepository.findById(p.accountId()).orElseThrow();auditService.record(actor,action,"Alert",a.getAlertId(),null,Map.of("status",a.getStatus().name()),md);}
    private Alert alert(UUID id){return alertRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Alert not found: "+id));}
}
