package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** A missing SOS roster never prevents a durable battery warning to administrators. */
@Service
@RequiredArgsConstructor
public class BatteryAlertService {
    private final AlertRepository alerts;
    private final AlertRecipientRepository recipients;
    private final AccountRepository accounts;
    private final RoomRepository rooms;
    private final ShiftRepository shifts;
    private final RobotBatteryPolicyRepository batteryPolicies;
    private final RuntimeSettingService settings;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void raise(Robot robot, UUID episodeId, String level, BigDecimal percent, String detail) {
        if (!List.of("LOW", "CRITICAL", "BLOCKED").contains(level))
            throw new IllegalArgumentException("Unknown battery warning level");
        String key = "BATTERY:" + robot.getRobotId() + ":" + episodeId + ":" + level;
        var existing = alerts.findByOperationKey(key).orElse(null);
        if (existing != null) {
            if ("BLOCKED".equals(level)) {
                var data = existing.getDataJson().deepCopy();
                if (data instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                    object.put("detail", detail).put("last_evaluated_at", clock.instant().toString());
                    if (percent == null) object.putNull("battery_percent"); else object.put("battery_percent", percent);
                    existing.setDataJson(object);
                }
            }
            return;
        }
        var now = clock.instant();
        var targets = new LinkedHashMap<UUID, Account>();
        accounts.findByStatusAndRoleInOrderByAccountCodeAsc(AccountStatus.ACTIVE, List.of(Role.ADMIN))
                .forEach(a -> targets.put(a.getAccountId(), a));
        UUID roomId = monitoringRoom(robot);
        List<Account> staff = roomId == null ? List.of()
                : shifts.findOnDutySosResponders(roomId, now, PageRequest.of(0, 100));
        staff.forEach(a -> targets.put(a.getAccountId(), a));

        String type = switch (level) {
            case "LOW" -> "LOW_BATTERY";
            case "CRITICAL" -> "CRITICAL_BATTERY";
            default -> "BATTERY_RETURN_BLOCKED";
        };
        String message = switch (level) {
            case "LOW" -> "Robot " + robot.getRobotCode() + " sắp hết pin";
            case "CRITICAL" -> "Robot " + robot.getRobotCode() + " cần dừng nhiệm vụ và về điểm sạc";
            default -> "Robot " + robot.getRobotCode() + " chưa thể về điểm sạc, cần quản trị viên hỗ trợ";
        };
        var policy = batteryPolicies.findById(robot.getRobotId()).orElse(null);
        boolean test = policy != null && ("TEST".equals(policy.getReturnSource()) || "TEST_EXPIRED".equals(policy.getReturnSource()));
        if (test) message = "[Kiểm thử] " + message;
        var data = json.createObjectNode().put("robot_id", robot.getRobotId().toString())
                .put("robot_code", robot.getRobotCode()).put("level", level)
                .put("episode_id", episodeId.toString()).put("observed_at", now.toString())
                .put("detail", detail).put("test_mode", test).put("estimate_method", test ? "SIMULATION" : "VOLTAGE_CURVE")
                .put("routing_status", staff.isEmpty() ? "ADMIN_ONLY" : "SOS_STAFF_AND_ADMIN");
        if (percent == null) data.putNull("battery_percent"); else data.put("battery_percent", percent);
        if (roomId == null) data.putNull("monitoring_room_id"); else data.put("monitoring_room_id", roomId.toString());
        if (staff.isEmpty()) data.putNull("staff_account_id");
        else data.put("staff_account_id", staff.getFirst().getAccountId().toString());
        data.set("staff_account_ids", json.valueToTree(staff.stream().map(Account::getAccountId).toList()));
        if (robot.getCurrentMap() != null) data.put("map_id", robot.getCurrentMap().getMapId().toString());
        var alert = alerts.saveAndFlush(Alert.builder().notificationType(type)
                .severity("LOW".equals(level) ? AlertSeverity.WARNING : AlertSeverity.CRITICAL)
                .status(AlertStatus.OPEN).message(message).dataJson(data)
                .requestedRecipientsJson(json.valueToTree(targets.keySet()))
                .requireAck(true).ackPolicy(AlertAckPolicy.ANY).operationKey(key).createdAt(now).build());
        for (var account : targets.values()) {
            recipients.save(AlertRecipient.builder().alert(alert).account(account).channel(AlertChannel.WEB)
                    .recipientAddressSnapshot(account.getUsername()).resolvedRoleSnapshot(account.getRole().name())
                    .deliveryStatus(AlertDeliveryStatus.PENDING).createdAt(now).build());
        }
    }

    private UUID monitoringRoom(Robot robot) {
        String configured = settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING, "");
        UUID id;
        try { id = UUID.fromString(configured.trim()); }
        catch (IllegalArgumentException exception) { return null; }
        var room = rooms.findById(id).orElse(null);
        if (room == null || !room.isActive() || room.getFloor() == null) return null;
        var map = robot.getCurrentMap();
        if (map != null && !map.getFloor().getHospitalInformation().getHospitalInformationId()
                .equals(room.getFloor().getHospitalInformation().getHospitalInformationId())) return null;
        // Notifications do not require an endpoint or a traversable route to this room.
        return id;
    }
}
