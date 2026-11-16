package com.mediassistrover.backend.scheduler;

import com.mediassistrover.backend.repository.ServiceRequestRepository;
import com.mediassistrover.backend.service.PendingEmergencyDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="app.operations.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class PendingEmergencyDispatchScheduler {
    private final ServiceRequestRepository requests;
    private final PendingEmergencyDispatchService pending;
    @Scheduled(fixedDelayString="${app.workflow.emergency-dispatch-delay-ms:1000}")
    public void run() {
        for(var id:requests.findPendingEmergencyIds(PageRequest.of(0,100))) {
            try{pending.dispatch(id);}
            catch(RuntimeException unavailable){log.debug("Pending SOS {} waits for dispatch readiness",id);}
        }
    }
}
