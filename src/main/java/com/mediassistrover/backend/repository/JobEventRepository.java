package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.JobEvent;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface JobEventRepository extends JpaRepository<JobEvent,UUID> {
    boolean existsByProducerSessionIdAndSequenceNo(UUID sessionId,long sequenceNo);
    Page<JobEvent> findByJobJobId(UUID jobId,Pageable pageable);
}
