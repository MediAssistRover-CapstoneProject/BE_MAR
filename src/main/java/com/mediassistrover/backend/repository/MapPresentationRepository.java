package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.MapPresentation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MapPresentationRepository extends JpaRepository<MapPresentation, UUID> { }
