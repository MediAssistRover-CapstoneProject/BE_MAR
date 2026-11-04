package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.RobotTestSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface RobotTestSubmissionRepository extends JpaRepository<RobotTestSubmission,String> {
    // The unique key serializes simultaneous retries without locking Robot
    // throughout workflow compilation. Reservation rolls back with job creation.
    @Modifying
    @Query(value="insert into RobotTestSubmission(request_key,actor_account_id,request_fingerprint,created_at) "
            +"values (:key,:actor,:fingerprint,:created) on duplicate key update request_key=request_key",nativeQuery=true)
    int reserve(@Param("key") String key,@Param("actor") UUID actor,@Param("fingerprint") String fingerprint,@Param("created") Instant created);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RobotTestSubmission s where s.requestKey=:key")
    Optional<RobotTestSubmission> lockByKey(@Param("key") String key);
}
