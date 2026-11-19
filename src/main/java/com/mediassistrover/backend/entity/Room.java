package com.mediassistrover.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import com.mediassistrover.backend.enums.MapStatus;

@Entity @Table(name = "Room") @EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Room {
    @Id @GeneratedValue @UuidGenerator @Column(name = "room_id", columnDefinition = "BINARY(16)") private UUID roomId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "floor_id", nullable = false) private Floor floor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "default_endpoint_group_id") private EndpointGroup defaultEndpointGroup;
    @Column(name = "room_code", nullable = false, length = 64, unique = true) private String roomCode;
    @Column(name = "room_name", nullable = false, length = 160) private String roomName;
    @Column(name = "room_type", nullable = false, length = 64) private String roomType;
    @Column(name = "sos_original_room_name", length = 160) private String sosOriginalRoomName;
    @Column(name = "sos_original_room_type", length = 64) private String sosOriginalRoomType;
    @Column(name = "department_code", length = 64) private String departmentCode;
    @Column(name = "description", columnDefinition = "TEXT") private String description;
    @Column(name = "is_active", nullable = false) private boolean active;
    @OneToMany(mappedBy = "room") @org.hibernate.annotations.BatchSize(size = 100)
    @Builder.Default private List<Endpoint> endpoints = new ArrayList<>();
    @Column(name = "is_public", nullable = false) private boolean publicVisible;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    // Map availability is derived so switching maps never overwrites an
    // administrator's explicit room setting, including a manually disabled room.
    @Transient
    public boolean isOperational() {
        return active && floor != null && floor.isActive() && endpoints.stream().anyMatch(endpoint ->
                endpoint.isActive() && endpoint.getMap().getStatus() == MapStatus.ACTIVE
                && endpoint.getMap().getFloor().getFloorId().equals(floor.getFloorId()));
    }

    @Transient
    public boolean isOperationalOn(FacilityMap map) {
        return map != null && map.getStatus() == MapStatus.ACTIVE && floor != null && map.getFloor() != null
                && floor.getFloorId().equals(map.getFloor().getFloorId()) && isOperational()
                && endpoints.stream().anyMatch(endpoint -> endpoint.isActive()
                    && endpoint.getMap().getMapId().equals(map.getMapId()));
    }
}
