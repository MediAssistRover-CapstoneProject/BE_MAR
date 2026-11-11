package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.Endpoint;
import com.mediassistrover.backend.entity.Room;
import com.mediassistrover.backend.entity.SystemSetting;
import com.mediassistrover.backend.enums.SystemSettingDataType;
import com.mediassistrover.backend.repository.EndpointRepository;
import com.mediassistrover.backend.repository.RoomRepository;
import com.mediassistrover.backend.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SosMonitoringRoomService {
    public static final String SETTING_KEY = "sos.monitoring-room-id";
    public static final String SOS_ROOM_TYPE = "SOS";
    private static final String DEFAULT_ROOM_TYPE = "GENERAL";

    private final SystemSettingRepository settings;
    private final RoomRepository rooms;
    private final EndpointRepository endpoints;
    private final Clock clock;

    public boolean isSosType(String value) {
        return value != null && SOS_ROOM_TYPE.equalsIgnoreCase(value.trim());
    }

    /**
     * Reconciles a Room after its regular create/update mutation. The baseline values must describe
     * the Room before it became SOS; for a directly-created SOS Room they are request name/GENERAL.
     */
    @Transactional
    public void reconcile(Room room, String baselineName, String baselineType, Account actor) {
        boolean sosRequested = isSosType(room.getRoomType());
        boolean hasSnapshot = room.getSosOriginalRoomName() != null || room.getSosOriginalRoomType() != null;
        if (!sosRequested && !hasSnapshot) return;

        SystemSetting setting = lockSetting();
        UUID configured = configuredRoomId(setting);
        if (sosRequested && room.isActive()) {
            promote(room, baselineName, baselineType, configured, setting, actor);
            return;
        }

        restore(room);
        if (Objects.equals(configured, room.getRoomId())) clear(setting, actor);
    }

    @Transactional
    public void releaseIfConfigured(Room room, Account actor) {
        SystemSetting setting = lockSetting();
        if (!Objects.equals(configuredRoomId(setting), room.getRoomId())) return;
        restore(room);
        clear(setting, actor);
    }

    @Transactional
    public void captureCreatedEndpoint(Endpoint endpoint) {
        if (endpoint.getRoom() == null || endpoint.getSosOriginalEndpointName() != null) return;
        SystemSetting setting = lockSetting();
        if (Objects.equals(configuredRoomId(setting), endpoint.getRoom().getRoomId()))
            endpoint.setSosOriginalEndpointName(endpoint.getEndpointName());
    }

    @Transactional
    public EndpointTransition prepareEndpointChange(Endpoint endpoint, Room nextRoom) {
        SystemSetting setting = lockSetting();
        UUID configured = configuredRoomId(setting);
        boolean wasSosEndpoint = endpoint.getRoom() != null
                && Objects.equals(configured, endpoint.getRoom().getRoomId());
        boolean willBeSosEndpoint = nextRoom != null && nextRoom.isActive() && isSosType(nextRoom.getRoomType())
                && Objects.equals(configured, nextRoom.getRoomId());
        return new EndpointTransition(
                wasSosEndpoint && !willBeSosEndpoint && endpoint.getSosOriginalEndpointName() != null,
                !wasSosEndpoint && willBeSosEndpoint && endpoint.getSosOriginalEndpointName() == null,
                endpoint.getEndpointName());
    }

    public void finishEndpointChange(Endpoint endpoint, EndpointTransition transition) {
        if (transition.restoreOriginalName()) {
            endpoint.setEndpointName(endpoint.getSosOriginalEndpointName());
            endpoint.setSosOriginalEndpointName(null);
        } else if (transition.captureOriginalName()) {
            endpoint.setSosOriginalEndpointName(transition.nameBeforeChange());
        }
    }

    private void promote(Room target, String baselineName, String baselineType, UUID configured,
                         SystemSetting setting, Account actor) {
        if (configured != null && !configured.equals(target.getRoomId())) {
            rooms.lockById(configured).ifPresent(this::restore);
        }
        if (target.getSosOriginalRoomName() == null)
            target.setSosOriginalRoomName(clean(baselineName, target.getRoomName()));
        if (target.getSosOriginalRoomType() == null)
            target.setSosOriginalRoomType(normalType(baselineType));
        captureEndpointNames(target);
        target.setRoomType(SOS_ROOM_TYPE);
        pointTo(setting, target, actor);
    }

    private void captureEndpointNames(Room room) {
        for (Endpoint endpoint : endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(room.getRoomId())) {
            if (endpoint.getSosOriginalEndpointName() == null)
                endpoint.setSosOriginalEndpointName(endpoint.getEndpointName());
        }
    }

    private void restore(Room room) {
        if (room.getSosOriginalRoomName() != null) room.setRoomName(room.getSosOriginalRoomName());
        if (room.getSosOriginalRoomType() != null) room.setRoomType(room.getSosOriginalRoomType());
        else if (isSosType(room.getRoomType())) room.setRoomType(DEFAULT_ROOM_TYPE);
        room.setSosOriginalRoomName(null);
        room.setSosOriginalRoomType(null);
        if (room.getRoomId() == null) return;
        for (Endpoint endpoint : endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(room.getRoomId())) {
            if (endpoint.getSosOriginalEndpointName() == null) continue;
            endpoint.setEndpointName(endpoint.getSosOriginalEndpointName());
            endpoint.setSosOriginalEndpointName(null);
        }
    }

    private SystemSetting lockSetting() {
        return settings.lockBySettingKey(SETTING_KEY).orElseGet(() -> settings.saveAndFlush(SystemSetting.builder()
                .settingKey(SETTING_KEY).settingValue(Map.of("value", ""))
                .dataType(SystemSettingDataType.STRING).category("SOS")
                .description("Room assigned to receive robot SOS calls")
                .publicSetting(false).active(true).updatedAt(Instant.now(clock)).build()));
    }

    private UUID configuredRoomId(SystemSetting setting) {
        if (!setting.isActive()) return null;
        Object value = setting.getSettingValue() == null ? null : setting.getSettingValue().get("value");
        if (!(value instanceof String text) || text.isBlank()) return null;
        try { return UUID.fromString(text.trim()); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private void pointTo(SystemSetting setting, Room room, Account actor) {
        setting.setSettingValue(Map.of("value", room.getRoomId().toString()));
        setting.setDataType(SystemSettingDataType.STRING);
        setting.setCategory("SOS");
        setting.setDescription("Room assigned to receive robot SOS calls");
        setting.setPublicSetting(false);
        setting.setActive(true);
        if (actor != null) setting.setUpdatedBy(actor);
        setting.setUpdatedAt(Instant.now(clock));
    }

    private void clear(SystemSetting setting, Account actor) {
        setting.setSettingValue(Map.of("value", ""));
        setting.setActive(true);
        if (actor != null) setting.setUpdatedBy(actor);
        setting.setUpdatedAt(Instant.now(clock));
    }

    private String normalType(String value) {
        return value == null || value.isBlank() || isSosType(value) ? DEFAULT_ROOM_TYPE : value.trim();
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record EndpointTransition(boolean restoreOriginalName, boolean captureOriginalName,
                                     String nameBeforeChange) { }
}
