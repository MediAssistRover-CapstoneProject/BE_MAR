package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from StaffProfile p where p.account.accountId=:id")
    Optional<StaffProfile> lockFace(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<StaffProfile> findByAccountAccountId(UUID accountId);
    boolean existsByStaffCode(String staffCode);
    boolean existsByStaffCodeIgnoreCase(String staffCode);
    boolean existsByUrl(String url);
    Page<StaffProfile> findByActiveTrue(Pageable pageable);
}
