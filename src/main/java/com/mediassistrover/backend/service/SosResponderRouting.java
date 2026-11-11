package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.EmergencyIncident;
import com.mediassistrover.backend.entity.EmergencyIncident.SosRoutingStatus;
import com.mediassistrover.backend.entity.FacilityMap;
import com.mediassistrover.backend.entity.Room;
import com.mediassistrover.backend.repository.RoomRepository;
import com.mediassistrover.backend.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

import static com.mediassistrover.backend.entity.EmergencyIncident.SosRoutingStatus.*;

@Component
@RequiredArgsConstructor
public class SosResponderRouting {
    public static final String MONITORING_ROOM_SETTING = "sos.monitoring-room-id";

    private final RuntimeSettingService settings;
    private final RoomRepository rooms;
    private final ShiftRepository shifts;

    public record Assignment(SosRoutingStatus status, Account responder) { }

    public void assign(EmergencyIncident incident, Instant now) {
        var assignment = resolve(incident.getMap(), now, null);
        incident.setSosRoutingStatus(assignment.status());
        if (assignment.responder() != null) incident.setSosResponder(assignment.responder());
    }

    public Assignment resolve(FacilityMap map, Instant now, UUID roomOverride) {
        String configuredRoom = roomOverride == null
                ? settings.getString(MONITORING_ROOM_SETTING, "") : roomOverride.toString();
        if (configuredRoom.isBlank()) {
            return new Assignment(NO_MONITORING_ROOM, null);
        }

        UUID roomId;
        try {
            roomId = UUID.fromString(configuredRoom.trim());
        } catch (IllegalArgumentException error) {
            return new Assignment(ROOM_UNAVAILABLE, null);
        }
        var room = rooms.findById(roomId).filter(Room::isActive).orElse(null);
        if (room == null || !room.getFloor().isActive() || !sameHospital(map, room)) {
            return new Assignment(ROOM_UNAVAILABLE, null);
        }

        // Two rows suffice to detect a conflicting roster.
        var candidates = shifts.findOnDutySosResponders(roomId, now, PageRequest.of(0, 2));
        if (candidates.size() == 1) {
            return new Assignment(ASSIGNED, candidates.getFirst());
        }
        return new Assignment(candidates.isEmpty() ? NO_ACTIVE_SHIFT : AMBIGUOUS_SHIFT, null);
    }

    private boolean sameHospital(FacilityMap map, Room room) {
        return map == null || map.getFloor().getHospitalInformation().getHospitalInformationId()
                .equals(room.getFloor().getHospitalInformation().getHospitalInformationId());
    }
}
