package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.HospitalInformationArticle;
import com.mediassistrover.backend.enums.HospitalInformationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HospitalInformationArticleRepository extends JpaRepository<HospitalInformationArticle, UUID> {
    boolean existsByHospitalInformationHospitalInformationIdAndArticleCodeIgnoreCaseAndLanguageCodeIgnoreCase(
            UUID hospitalId, String articleCode, String languageCode);

    @Query("""
            select a from HospitalInformationArticle a
            where a.hospitalInformation.hospitalInformationId = :hospitalId
              and (:status is null or a.status = :status)
            """)
    Page<HospitalInformationArticle> search(@Param("hospitalId") UUID hospitalId,
                                            @Param("status") HospitalInformationStatus status,
                                            Pageable pageable);

    @Query("""
            select a from HospitalInformationArticle a
            where a.status = :status and a.publicVisible = true
              and lower(a.languageCode) = lower(:languageCode)
              and (a.effectiveFrom is null or a.effectiveFrom <= :now)
              and (a.effectiveUntil is null or a.effectiveUntil >= :now)
            order by a.updatedAt desc
            """)
    List<HospitalInformationArticle> findEffectiveCandidates(@Param("status") HospitalInformationStatus status,
                                                              @Param("languageCode") String languageCode,
                                                              @Param("now") Instant now,
                                                              Pageable pageable);
}
