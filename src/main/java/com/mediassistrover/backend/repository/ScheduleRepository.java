package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.Schedule;
import com.mediassistrover.backend.enums.ScheduleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface ScheduleRepository extends JpaRepository<Schedule,UUID> {
    boolean existsByScheduleCodeIgnoreCase(String code);
    Page<Schedule> findByStatus(ScheduleStatus status,Pageable pageable);
    Page<Schedule> findByStatusNot(ScheduleStatus status,Pageable pageable);
    Page<Schedule> findByCreatedByAccountAccountId(UUID accountId,Pageable pageable);
    Page<Schedule> findByCreatedByAccountAccountIdAndStatus(UUID accountId,ScheduleStatus status,Pageable pageable);
    Page<Schedule> findByCreatedByAccountAccountIdAndStatusNot(UUID accountId,ScheduleStatus status,Pageable pageable);
    @Query("select count(s)>0 from Schedule s where s.status=com.mediassistrover.backend.enums.ScheduleStatus.ACTIVE " +
            "and s.robot.robotId=:robotId and (:excludeId is null or s.scheduleId<>:excludeId) " +
            "and (s.workflow.serviceType<>'PATROL' or substring(s.workflow.workflowCode,1,10)<>'WF_PATROL_') " +
            "and (:endsAt is null or s.startsAt<:endsAt) and (s.endsAt is null or s.endsAt>:startsAt)")
    boolean existsActiveRobotCollision(@Param("robotId") UUID robotId,@Param("startsAt") Instant startsAt,
                                       @Param("endsAt") Instant endsAt,@Param("excludeId") UUID excludeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.status='ACTIVE' and s.nextRunAt is not null and s.nextRunAt <= :now order by s.nextRunAt, s.scheduleId")
    List<Schedule> lockDue(@Param("now") Instant now,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.status='ACTIVE' and s.nextRunAt <= :now " +
            "and (s.nextRunAt > :afterAt or (s.nextRunAt = :afterAt and s.scheduleId > :afterId)) " +
            "order by s.nextRunAt, s.scheduleId")
    List<Schedule> lockDueAfter(@Param("now") Instant now,@Param("afterAt") Instant afterAt,
                                @Param("afterId") UUID afterId,Pageable pageable);
}
