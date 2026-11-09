package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.EmergencyIncident;
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

    public void assign(EmergencyIncident incident, Instant now) {
        String configuredRoom = settings.getString(MONITORING_ROOM_SETTING, "");
        if (configuredRoom.isBlank()) {
            incident.setSosRoutingStatus(NO_MONITORING_ROOM);
            return;
        }

        UUID roomId;
        try {
            roomId = UUID.fromString(configuredRoom.trim());
        } catch (IllegalArgumentException error) {
            incident.setSosRoutingStatus(ROOM_UNAVAILABLE);
            return;
        }
        var room = rooms.findById(roomId).filter(Room::isActive).orElse(null);
        if (room == null || !room.getFloor().isActive() || !sameHospital(incident, room)) {
            incident.setSosRoutingStatus(ROOM_UNAVAILABLE);
            return;
        }

        // Two rows suffice to detect a conflicting roster. The recipient is fixed for this incident.
        var candidates = shifts.findOnDutySosResponders(roomId, now, PageRequest.of(0, 2));
        if (candidates.size() == 1) {
            incident.setSosResponder(candidates.getFirst());
            incident.setSosRoutingStatus(ASSIGNED);
        } else {
            incident.setSosRoutingStatus(candidates.isEmpty() ? NO_ACTIVE_SHIFT : AMBIGUOUS_SHIFT);
        }
    }

    private boolean sameHospital(EmergencyIncident incident, Room room) {
        var map = incident.getMap();
        return map == null || map.getFloor().getHospitalInformation().getHospitalInformationId()
                .equals(room.getFloor().getHospitalInformation().getHospitalInformationId());
    }
}
