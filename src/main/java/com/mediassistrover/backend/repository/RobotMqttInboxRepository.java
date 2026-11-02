package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.RobotMqttInbox;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface RobotMqttInboxRepository extends JpaRepository<RobotMqttInbox,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from RobotMqttInbox i where i.messageId=:id")
    Optional<RobotMqttInbox> lock(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RobotMqttInbox> findByProducerSessionIdAndSequenceNo(UUID producer,long sequence);
    @Modifying(flushAutomatically=true)
    @Query(value="INSERT IGNORE INTO RobotMqttInbox(message_id,robot_id,producer_session_id,sequence_no,transport_session_id,message_type,canonical_hash,raw_envelope,state,received_at) VALUES (:id,:robot,:producer,:sequence,:session,:type,:hash,:raw,'PENDING',:now)",nativeQuery=true)
    void insert(@Param("id") UUID id,@Param("robot") UUID robot,@Param("producer") UUID producer,@Param("sequence") long sequence,
                @Param("session") UUID session,@Param("type") String type,@Param("hash") String hash,@Param("raw") String raw,@Param("now") Instant now);
    @Modifying(flushAutomatically=true)
    @Query(value="INSERT IGNORE INTO RobotMqttInbox(message_id,robot_id,producer_session_id,sequence_no,transport_session_id,message_type,canonical_hash,raw_envelope,state,accepted,response_json,received_at,processed_at) VALUES (:id,:robot,:producer,:sequence,:session,:type,:hash,:raw,'PROCESSED',TRUE,:response,:now,:now)",nativeQuery=true)
    int insertCommittedStart(@Param("id") UUID id,@Param("robot") UUID robot,@Param("producer") UUID producer,@Param("sequence") long sequence,
                @Param("session") UUID session,@Param("type") String type,@Param("hash") String hash,@Param("raw") String raw,
                @Param("response") String response,@Param("now") Instant now);
    @Modifying
    @Query("update RobotMqttInbox i set i.ackPublishedAt=:now where i.messageId=:id and i.transportSessionId=:session and i.messageType<>'HELLO'")
    int markEventAcknowledged(@Param("id") UUID id,@Param("session") UUID session,@Param("now") Instant now);
    @Query("select i.messageId from RobotMqttInbox i where i.state='PENDING' order by case when i.messageType='HELLO' then 0 else 1 end, i.receivedAt")
    List<UUID> pending(Pageable page);
    @Query("select i.messageId from RobotMqttInbox i where i.responseJson is not null and i.ackPublishedAt is null order by i.receivedAt")
    List<UUID> acknowledgements(Pageable page);
}
