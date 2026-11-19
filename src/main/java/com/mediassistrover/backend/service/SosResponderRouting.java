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

    public record Assignment(SosRoutingStatus status, Account responder, UUID roomId, String roomName, String reason) { }

    public void assign(EmergencyIncident incident, Instant now) {
        var assignment = resolve(incident.getMap(), now, null);
        incident.setSosRoutingStatus(assignment.status());
        if (assignment.responder() != null) incident.setSosResponder(assignment.responder());
    }

    public Assignment resolve(FacilityMap map, Instant now, UUID roomOverride) {
        String configuredRoom = roomOverride == null
                ? settings.getString(MONITORING_ROOM_SETTING, "") : roomOverride.toString();
        if (configuredRoom.isBlank()) {
            return new Assignment(NO_MONITORING_ROOM, null, null, null, "MONITORING_ROOM_MISSING");
        }

        UUID roomId;
        try {
            roomId = UUID.fromString(configuredRoom.trim());
        } catch (IllegalArgumentException error) {
            return new Assignment(ROOM_UNAVAILABLE, null, null, null, "MONITORING_ROOM_INVALID");
        }
        var room = rooms.findById(roomId).orElse(null);
        if (room == null) return unavailable(roomId, null, "ROOM_MISSING");
        if (!room.isActive()) return unavailable(roomId, room, "ROOM_INACTIVE");
        if (room.getFloor() == null || !room.getFloor().isActive())
            return unavailable(roomId, room, "FLOOR_INACTIVE");
        if (!sameHospital(map, room)) return unavailable(roomId, room, "DIFFERENT_HOSPITAL");
        if (!room.isOperational()) return unavailable(roomId, room, "NO_ENDPOINT_ON_ACTIVE_MAP");
        if (map != null && !room.isOperationalOn(map))
            return unavailable(roomId, room, "NO_ENDPOINT_ON_INCIDENT_MAP");

        // Two rows suffice to detect a conflicting roster.
        var candidates = shifts.findOnDutySosResponders(roomId, now, PageRequest.of(0, 2));
        if (candidates.size() == 1) {
            return new Assignment(ASSIGNED, candidates.getFirst(), roomId, room.getRoomName(), "READY");
        }
        return new Assignment(candidates.isEmpty() ? NO_ACTIVE_SHIFT : AMBIGUOUS_SHIFT, null,
                roomId, room.getRoomName(), candidates.isEmpty() ? "NO_CHECKED_IN_STAFF" : "MULTIPLE_CHECKED_IN_STAFF");
    }

    private Assignment unavailable(UUID roomId, Room room, String reason) {
        return new Assignment(ROOM_UNAVAILABLE, null, roomId, room == null ? null : room.getRoomName(), reason);
    }

    private boolean sameHospital(FacilityMap map, Room room) {
        return map == null || map.getFloor().getHospitalInformation().getHospitalInformationId()
                .equals(room.getFloor().getHospitalInformation().getHospitalInformationId());
    }
}
