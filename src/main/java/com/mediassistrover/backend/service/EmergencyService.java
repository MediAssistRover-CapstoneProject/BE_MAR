package com.mediassistrover.backend.service;
import com.mediassistrover.backend.dto.emergency.request.EmergencyRequests.*;
import com.mediassistrover.backend.dto.emergency.response.EmergencyResponses.*;
import com.mediassistrover.backend.dto.pi.request.PiEnvelope;
import com.mediassistrover.backend.enums.EmergencyIncidentStatus;
import com.mediassistrover.backend.security.*;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface EmergencyService {
    SosResponse createSos(PiEnvelope<SosPayload> envelope,DevicePrincipal actor,RequestMetadata metadata);
    IncidentResponse get(UUID id);
    Page<IncidentResponse> list(EmergencyIncidentStatus status,Pageable pageable);
    IncidentResponse acknowledge(UUID id,AccountPrincipal actor,RequestMetadata metadata);
    IncidentResponse start(UUID id,AccountPrincipal actor,RequestMetadata metadata);
    IncidentResponse resolve(UUID id,Resolution request,AccountPrincipal actor,RequestMetadata metadata);
    IncidentResponse close(UUID id,AccountPrincipal actor,RequestMetadata metadata);
}
