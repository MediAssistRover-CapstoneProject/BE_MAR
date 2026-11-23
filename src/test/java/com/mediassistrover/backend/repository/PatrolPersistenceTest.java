package com.mediassistrover.backend.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated ORM/query validation; never connects to the application database or MQTT. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
    "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true","logging.level.org.hibernate.SQL=WARN","logging.level.org.springframework.jdbc=WARN"})
@ContextConfiguration(classes=PatrolPersistenceTest.Config.class)
class PatrolPersistenceTest {
    @Configuration @EnableAutoConfiguration
    @EntityScan(basePackages={"com.mediassistrover.backend.entity","com.mediassistrover.backend.media"})
    @EnableJpaRepositories(basePackages={"com.mediassistrover.backend.repository","com.mediassistrover.backend.media"})
    static class Config { }
    @Autowired EntityManager em;
    @Autowired JobRepository jobs;
    @Autowired JobSuspensionHoldRepository holds;
    @Autowired ScheduleRepository schedules;

    @Test void suspendedJobAndIndependentHoldsPersistAndRoundQueriesCompile(){
        var json=new ObjectMapper();var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var workflow=Workflow.builder().workflowCode("WF_TEST_PATROL").versionNo(1).workflowName("Patrol")
            .serviceType("PATROL").status(WorkflowStatus.PUBLISHED).acceptsNewJobs(true)
            .schemaVersion(3).graphVersion(3).maxDurationSec(600).maxTotalVisits(10)
            .inputSchemaJson(json.createObjectNode()).inputUiJson(json.createObjectNode()).defaultInputsJson(json.createObjectNode())
            .outputMappingJson(json.createObjectNode()).requiredCapabilitiesJson(json.createArrayNode()).createdAt(now).updatedAt(now).build();
        em.persist(workflow);
        var job=Job.builder().workflow(workflow).jobCode("JOB_TEST_PATROL").status(JobStatus.SUSPENDED).priority(1)
            .inputJson(json.createObjectNode()).contextJson(json.createObjectNode()).workflowHash("a".repeat(64))
            .protocolVersion(1).schemaVersion(3).graphVersion(3).beOrchestrated(true).queuedAt(now).startedAt(now)
            .suspendedAt(now).resumeRequested(true).patrolRepeatCount(3).patrolRound(2).build();
        em.persist(job);UUID incident=UUID.randomUUID(),voice=UUID.randomUUID();
        holds.save(JobSuspensionHold.builder().job(job).source("INCIDENT").sourceId(incident).createdAt(now).build());
        holds.save(JobSuspensionHold.builder().job(job).source("VOICE").sourceId(voice).createdAt(now).build());
        em.flush();UUID id=job.getJobId();em.clear();
        var restored=jobs.findById(id).orElseThrow();assertEquals(JobStatus.SUSPENDED,restored.getStatus());
        assertEquals(2,restored.getPatrolRound());assertEquals(3,restored.getPatrolRepeatCount());
        assertTrue(holds.existsByJobJobIdAndReleasedAtIsNull(id));
        var voiceHold=holds.findByJobJobIdAndSourceAndSourceId(id,"VOICE",voice).orElseThrow();voiceHold.setReleasedAt(now);
        em.flush();assertTrue(holds.existsByJobJobIdAndReleasedAtIsNull(id),"Incident still owns a separate hold");
        assertDoesNotThrow(()->jobs.findBeDeadlineCandidates(org.springframework.data.domain.PageRequest.of(0,10)));
        restored.setStatus(JobStatus.RUNNING);restored.setPatrolRoundStartedAt(now.minusSeconds(10));
        restored.setSuspendedDurationMs(15000);restored.setPatrolRoundSuspendedBaseMs(5000);em.flush();
        var deadline=jobs.findBeDeadlineCandidates(org.springframework.data.domain.PageRequest.of(0,10)).getFirst();
        assertEquals(15000L,deadline.getSuspendedDurationMs());
        assertEquals(5000L,deadline.getPatrolRoundSuspendedBaseMs());
        assertNotNull(deadline.getPatrolRoundStartedAt());
        assertDoesNotThrow(()->jobs.findYieldablePatrolCandidates(org.springframework.data.domain.PageRequest.of(0,10)));
        assertDoesNotThrow(()->jobs.findPatrolArbitrationRobotIds(org.springframework.data.domain.PageRequest.of(0,10)));
        for(int i=0;i<3;i++)em.persist(Schedule.builder().workflow(workflow).scheduleCode("SCHEDULE_"+i)
                .scheduleName("Patrol "+i).scheduleType(ScheduleType.ONCE).timezone("Asia/Ho_Chi_Minh")
                .startsAt(now.minusSeconds(60)).nextRunAt(now).status(ScheduleStatus.ACTIVE)
                .misfirePolicy(MisfirePolicy.FIRE_ONCE).inputTemplateJson(json.createObjectNode()).createdAt(now).updatedAt(now).build());
        em.flush();var page=org.springframework.data.domain.PageRequest.of(0,1);
        var first=schedules.lockDue(now,page).getFirst();
        var second=schedules.lockDueAfter(now,first.getNextRunAt(),first.getScheduleId(),page).getFirst();
        var third=schedules.lockDueAfter(now,second.getNextRunAt(),second.getScheduleId(),page).getFirst();
        assertEquals(3,Set.of(first.getScheduleId(),second.getScheduleId(),third.getScheduleId()).size());
        assertTrue(schedules.lockDueAfter(now,third.getNextRunAt(),third.getScheduleId(),page).isEmpty());
        for(int i=0;i<3;i++)em.persist(Job.builder().workflow(workflow).jobCode("JOB_QUEUE_"+i)
                .status(JobStatus.QUEUED).priority(i==0?90:80).queuedAt(now)
                .schemaVersion(3).graphVersion(3).protocolVersion(1).beOrchestrated(true)
                .workflowHash("a".repeat(64)).inputJson(json.createObjectNode()).contextJson(json.createObjectNode()).build());
        em.flush();
        var head=jobs.findById(jobs.findUnassignedQueuedJobIds(page).getFirst()).orElseThrow();
        var tail=jobs.findById(jobs.findUnassignedQueuedJobIdsAfter(head.getPriority(),head.getQueuedAt(),head.getJobId(),page)
                .getFirst()).orElseThrow();
        var last=jobs.findById(jobs.findUnassignedQueuedJobIdsAfter(tail.getPriority(),tail.getQueuedAt(),tail.getJobId(),page)
                .getFirst()).orElseThrow();
        assertEquals(90,head.getPriority());assertEquals(80,tail.getPriority());
        assertEquals(3,Set.of(head.getJobId(),tail.getJobId(),last.getJobId()).size());
        assertTrue(jobs.findUnassignedQueuedJobIdsAfter(last.getPriority(),last.getQueuedAt(),last.getJobId(),page).isEmpty());
        tail.setCancelRequestedAt(now);em.flush();
        assertEquals(List.of(last.getJobId()),jobs.findUnassignedQueuedJobIdsAfter(head.getPriority(),head.getQueuedAt(),head.getJobId(),page));
    }
}
