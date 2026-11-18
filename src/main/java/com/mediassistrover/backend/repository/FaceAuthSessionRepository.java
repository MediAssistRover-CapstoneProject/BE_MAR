package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.FaceAuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FaceAuthSessionRepository extends JpaRepository<FaceAuthSession,UUID> {
    Optional<FaceAuthSession> findByAuthJobStepId(UUID id);
}
