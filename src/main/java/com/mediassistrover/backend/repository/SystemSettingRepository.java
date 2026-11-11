package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemSetting s where s.settingKey = :key")
    Optional<SystemSetting> lockBySettingKey(@Param("key") String key);
    Optional<SystemSetting> findBySettingKey(String settingKey);
    Optional<SystemSetting> findBySettingKeyAndActiveTrue(String settingKey);
    boolean existsBySettingKey(String settingKey);
    Page<SystemSetting> findByCategoryIgnoreCase(String category, Pageable pageable);
    Page<SystemSetting> findByActiveTrue(Pageable pageable);
    Page<SystemSetting> findByCategoryIgnoreCaseAndActiveTrue(String category, Pageable pageable);
}
