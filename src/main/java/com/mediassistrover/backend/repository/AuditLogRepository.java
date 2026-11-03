package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    @Query(value="select exists(select 1 from AuditLog where JSON_SEARCH(before_data_json,'one',:value) is not null or JSON_SEARCH(after_data_json,'one',:value) is not null)",nativeQuery=true)
    boolean containsJsonString(@Param("value") String value);
}
