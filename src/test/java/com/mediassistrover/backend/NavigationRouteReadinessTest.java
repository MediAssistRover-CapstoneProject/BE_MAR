package com.mediassistrover.backend;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.mapping.MapBundleService;
import com.mediassistrover.backend.mqtt.MqttWire;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.NavigationSessionService;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No DB or robot: admission evidence is scoped to one locked route transaction. */
class NavigationRouteReadinessTest {
    final UUID robot=UUID.randomUUID(),session=UUID.randomUUID(),map=UUID.randomUUID();
    final Instant start=Instant.parse("2026-11-02T11:00:00Z");
    final Clock clock=mock(Clock.class);
    final RobotNavigationSessionRepository rows=mock(RobotNavigationSessionRepository.class);
    final RobotMqttSessionRepository links=mock(RobotMqttSessionRepository.class);
    final RobotNavigationSession row=new RobotNavigationSession();
    final RobotMqttSession link=new RobotMqttSession();
    final NavigationSessionService service=spy(new NavigationSessionService(rows,mock(RobotRepository.class),
        mock(FacilityMapRepository.class),links,mock(RobotNavigationProfileRepository.class),mock(JobRepository.class),
        mock(EndpointRepository.class),mock(MapBundleService.class),new MqttWire(),clock));
    @BeforeEach void setup(){
        when(clock.instant()).thenReturn(start);
        row.setRobotId(robot);row.setSessionId(session);row.setInitializationRevision(1);
        row.setMap(FacilityMap.builder().mapId(map).build());row.setExpiresAt(start.plusSeconds(600));
        link.setDispatchReady(true);link.setTransportSessionId(UUID.randomUUID());link.setProducerSessionId(UUID.randomUUID());
        link.setLastHeartbeatAt(start.minusSeconds(1));
        when(rows.findById(robot)).thenReturn(Optional.of(row));when(links.findById(robot)).thenReturn(Optional.of(link));
        doReturn(true).when(service).localized(robot,link);
        TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();
    }
    void complete(){
        for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @AfterEach void cleanup(){if(TransactionSynchronizationManager.isSynchronizationActive())complete();}
    @Test void slowGraphCreationDoesNotExpireItsOwnAdmissionWhileRobotLockBlocksNewHeartbeat(){
        service.pinReadyContextForLockedRoute(robot,session,map);
        when(clock.instant()).thenReturn(start.plusSeconds(12));
        assertThatCode(()->service.requireReadyContext(robot,session,map)).doesNotThrowAnyException();
        verify(service,times(1)).localized(robot,link);
    }
    @Test void proofIsDiscardedAtCommitAndCannotAuthorizeAnotherRequest(){
        service.pinReadyContextForLockedRoute(robot,session,map);complete();
        when(clock.instant()).thenReturn(start.plusSeconds(12));
        assertThatThrownBy(()->service.requireReadyContext(robot,session,map)).hasMessageContaining("NOT_LOCALIZED");
    }
    @Test void closedOrExpiredSessionsStillRejectInsideTheSameTransaction(){
        service.pinReadyContextForLockedRoute(robot,session,map);row.setClosedAt(start);
        assertThatThrownBy(()->service.requireReadyContext(robot,session,map)).hasMessageContaining("CLOSED_OR_CHANGED");
    }
    @Test void changedInitializationOrTransportCannotReuseTheProof(){
        service.pinReadyContextForLockedRoute(robot,session,map);when(clock.instant()).thenReturn(start.plusSeconds(12));
        row.setInitializationRevision(2);
        assertThatThrownBy(()->service.requireReadyContext(robot,session,map)).hasMessageContaining("NOT_LOCALIZED");
        row.setInitializationRevision(1);link.setTransportSessionId(UUID.randomUUID());
        assertThatThrownBy(()->service.requireReadyContext(robot,session,map)).hasMessageContaining("NOT_LOCALIZED");
    }
    @Test void suspendedTransactionDoesNotLendItsProofToANewTransaction(){
        service.pinReadyContextForLockedRoute(robot,session,map);
        var sync=TransactionSynchronizationManager.getSynchronizations().getFirst();sync.suspend();
        when(clock.instant()).thenReturn(start.plusSeconds(12));
        assertThatThrownBy(()->service.requireReadyContext(robot,session,map)).hasMessageContaining("NOT_LOCALIZED");
        sync.resume();assertThatCode(()->service.requireReadyContext(robot,session,map)).doesNotThrowAnyException();
    }
    @Test void pinningNeverMakesAnInitiallyStalePoseReady(){
        link.setLastHeartbeatAt(start.minusSeconds(8));
        assertThatThrownBy(()->service.pinReadyContextForLockedRoute(robot,session,map)).hasMessageContaining("NOT_LOCALIZED");
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }
}
