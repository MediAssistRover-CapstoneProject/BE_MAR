package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.MapPresentation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface MapPresentationRepository extends JpaRepository<MapPresentation, UUID> {
    @Query("""
            select p from MapPresentation p, FacilityMap m
            where p.mapId = m.mapId and m.floor.floorId = :floorId
              and p.mapId <> :mapId and p.publishedJson is not null
            """)
    List<MapPresentation> findOtherPublishedOnFloor(@Param("floorId") UUID floorId, @Param("mapId") UUID mapId);
}
