package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {
    Optional<SystemSetting> findBySettingKey(String settingKey);
    Optional<SystemSetting> findBySettingKeyAndActiveTrue(String settingKey);
    boolean existsBySettingKey(String settingKey);
    Page<SystemSetting> findByCategoryIgnoreCase(String category, Pageable pageable);
    Page<SystemSetting> findByActiveTrue(Pageable pageable);
    Page<SystemSetting> findByCategoryIgnoreCaseAndActiveTrue(String category, Pageable pageable);
}
