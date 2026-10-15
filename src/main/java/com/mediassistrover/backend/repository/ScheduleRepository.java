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
    Page<Schedule> findByCreatedByAccountAccountId(UUID accountId,Pageable pageable);
    Page<Schedule> findByCreatedByAccountAccountIdAndStatus(UUID accountId,ScheduleStatus status,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.status='ACTIVE' and s.nextRunAt is not null and s.nextRunAt <= :now order by s.nextRunAt")
    List<Schedule> lockDue(@Param("now") Instant now,Pageable pageable);
}
