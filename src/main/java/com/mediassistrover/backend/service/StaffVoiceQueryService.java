package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Live answers only. Staff names, shift status and rooms must never come from a PDF or LLM. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class StaffVoiceQueryService {
    private final StaffProfileRepository profiles;
    private final StaffDutyService duty;
    private final FacilityMapRepository maps;
    private final EndpointRepository endpoints;
    private final NavigationSessionService sessions;
    private final Clock clock;

    public record Query(@NotBlank @Size(max = 300) String question, UUID mapId) {}
    public record Answer(boolean recognized, String code, String answer, Instant checkedAt) {}
    private static final Pattern NAME = Pattern.compile("(?:^| )(?:bac si|bs|nhan vien) (?:nao )?(?:co )?(?:ten(?: la)? |la )?(.+?)(?= (?:dang|hien|o|co|truc|ca|hom|check|lam|trong|khong|chua|hay)(?: |$)|$)");

    public Answer answerForRobot(String question, UUID robotId) {
        RobotNavigationSession session = sessions.current(robotId);
        return answer(question, session == null ? null : session.getMap().getMapId());
    }

    public Answer answer(String question, UUID mapId) {
        String text = normalized(question).replaceAll("\\bdg\\b", "dang");
        if (text.isBlank()) return unknown();
        boolean aboutDoctor = text.contains("bac si") || text.startsWith("bs ") || text.contains(" bs ");
        boolean aboutStaff = text.contains("nhan vien");
        boolean roomQuery = text.contains("phong") &&
                (text.contains("bac si nao") || text.contains("ai dang truc") || text.contains("ai truc")
                        || text.contains("nhan vien nao dang truc"));
        boolean personQuery = (aboutDoctor || aboutStaff) && !roomQuery &&
                (text.contains("phong nao") || text.contains("o dau") || text.contains("truc")
                        || text.contains("check in") || text.contains("dang o")
                        || text.contains("hien o") || text.contains("benh vien") || text.contains("ca lam")
                        || text.contains(" ten ") || text.startsWith("co "));
        if (!roomQuery && !personQuery) return unknown();
        Instant now = clock.instant();
        return roomQuery ? inRoom(text, mapId, now) : findPerson(text, aboutDoctor, now);
    }

    private Answer findPerson(String question, boolean askedForDoctor, Instant now) {
        Matcher matcher = NAME.matcher(question);
        if (!matcher.find() || matcher.group(1).matches("(?:do|nay|nao|ay|kia|ten|la|co)(?: (?:do|nay|nao|ay|kia|ten|la|co))*"))
            return result("NAME_REQUIRED", "Bạn vui lòng nói rõ họ tên "
                    + (askedForDoctor ? "bác sĩ" : "nhân viên") + " cần tìm.", now);
        String target = matcher.group(1).trim();
        List<StaffProfile> candidates = profiles.findVoiceDirectory().stream()
                .filter(this::availableStaff)
                .filter(p -> matches(target, normalized(p.getAccount().getFullName()))
                        || matches(target, normalized(p.getStaffCode())))
                .toList();
        List<StaffProfile> exact = candidates.stream()
                .filter(p -> target.equals(normalized(p.getAccount().getFullName()))
                        || target.equals(normalized(p.getStaffCode()))).toList();
        if (!exact.isEmpty()) candidates = exact;
        if (candidates.isEmpty())
            return result(askedForDoctor ? "DOCTOR_NOT_FOUND" : "STAFF_NOT_FOUND",
                    "Tôi chưa tìm thấy " + (askedForDoctor ? "bác sĩ" : "nhân viên")
                            + " này trong danh sách hiện tại. Bạn vui lòng đọc lại họ tên hoặc mã nhân viên.", now);
        if (candidates.size() > 1)
            return result(askedForDoctor ? "DOCTOR_AMBIGUOUS" : "STAFF_AMBIGUOUS",
                    "Có nhiều " + (askedForDoctor ? "bác sĩ" : "nhân viên")
                            + " trùng tên. Bạn vui lòng nói đầy đủ họ tên hoặc mã nhân viên.", now);
        StaffProfile profile = candidates.getFirst();
        String fullName = profile.getAccount().getFullName();
        List<String> rooms = duty.list(null, null).stream()
                .filter(s -> s.accountId().equals(profile.getAccount().getAccountId()))
                .map(StaffDutyService.Duty::roomName).distinct().toList();
        if (!isDoctor(profile)) {
            if (!rooms.isEmpty())
                return result(askedForDoctor ? "STAFF_ON_DUTY_NOT_DOCTOR" : "STAFF_ON_DUTY",
                        "Nhân viên " + fullName + " đang trực tại " + String.join(" và ", rooms) + "."
                                + (askedForDoctor ? " Hồ sơ hiện chưa xác nhận người này là bác sĩ." : ""), now);
            return result("STAFF_OFF_DUTY", "Bệnh viện có nhân viên " + fullName
                    + ", nhưng hiện tại không trong ca trực."
                    + (askedForDoctor ? " Hồ sơ hiện chưa xác nhận người này là bác sĩ." : ""), now);
        }
        if (rooms.isEmpty())
            return result("DOCTOR_OFF_DUTY", "Bệnh viện có bác sĩ " + fullName
                    + ", nhưng hiện tại không trong ca trực.", now);
        return result("DOCTOR_ON_DUTY", "Bác sĩ " + fullName + " đang trực tại " + String.join(" và ", rooms) + ".", now);
    }

    private Answer inRoom(String question, UUID mapId, Instant now) {
        if (mapId == null) return result("MAP_REQUIRED", "Robot chưa có bản đồ đang hoạt động để tra phòng trực.", now);
        FacilityMap map = maps.findById(mapId).orElse(null);
        if (map == null || map.getStatus() != MapStatus.ACTIVE)
            return result("MAP_REQUIRED", "Bản đồ này hiện không hoạt động để tra phòng trực.", now);
        List<Room> rooms = endpoints.findByMapMapIdOrderByEndpointCodeAsc(mapId).stream()
                .filter(e -> e.isActive() && e.getRoom() != null && e.getRoom().isOperationalOn(map))
                .map(Endpoint::getRoom).distinct().filter(r -> question.contains(normalized(r.getRoomName())))
                .toList();
        if (rooms.isEmpty()) return result("ROOM_NOT_FOUND", "Tôi không tìm thấy phòng đó trên bản đồ đang hoạt động.", now);
        int longest = rooms.stream().mapToInt(r -> normalized(r.getRoomName()).length()).max().orElse(0);
        rooms = rooms.stream().filter(r -> normalized(r.getRoomName()).length() == longest).toList();
        if (rooms.size() > 1) return result("ROOM_AMBIGUOUS", "Bạn vui lòng nói rõ tên phòng cần hỏi.", now);
        Room room = rooms.getFirst();
        List<StaffProfile> directory = profiles.findVoiceDirectory().stream().filter(this::availableStaff).toList();
        Set<UUID> staffAccounts = directory.stream().map(p -> p.getAccount().getAccountId())
                .collect(Collectors.toSet());
        Set<UUID> doctorAccounts = directory.stream().filter(this::isDoctor)
                .map(p -> p.getAccount().getAccountId()).collect(Collectors.toSet());
        List<StaffDutyService.Duty> onDuty = duty.list(mapId, room.getRoomId()).stream()
                .filter(s -> staffAccounts.contains(s.accountId())).toList();
        List<String> names = onDuty.stream()
                .filter(s -> doctorAccounts.contains(s.accountId()))
                .map(StaffDutyService.Duty::fullName).distinct().toList();
        if (names.isEmpty() && !onDuty.isEmpty()) {
            String staffNames = onDuty.stream().map(StaffDutyService.Duty::fullName).distinct()
                    .collect(Collectors.joining(", "));
            return result("ROOM_STAFF_NO_DOCTOR", "Hiện tại " + room.getRoomName()
                    + " có nhân viên " + staffNames
                    + " đang check-in trực. Hồ sơ chưa xác nhận có bác sĩ đang trực tại phòng này.", now);
        }
        if (names.isEmpty())
            return result("ROOM_NO_DOCTOR", "Hiện tại không có bác sĩ nào check-in trực tại " + room.getRoomName() + ".", now);
        return result("ROOM_DOCTORS", "Bác sĩ đang check-in trực tại " + room.getRoomName() + ": " + String.join(", ", names) + ".", now);
    }

    private boolean availableStaff(StaffProfile profile) {
        Account account = profile.getAccount();
        return profile.isActive() && account.getStatus() == AccountStatus.ACTIVE
                && account.getRole() == Role.STAFF && !account.isMustChangePassword()
                && (account.getLockedUntil() == null || !account.getLockedUntil().isAfter(clock.instant()));
    }

    private boolean isDoctor(StaffProfile profile) {
        String title = normalized(profile.getJobTitle());
        return title.contains("bac si") || title.contains("doctor") || title.contains("physician");
    }

    private static boolean matches(String target, String name) {
        return !target.isBlank() && (" " + name + " ").contains(" " + target + " ");
    }

    private static String normalized(String value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", " ");
        return ascii.trim().replaceAll(" +", " ");
    }

    private static Answer unknown() { return new Answer(false, "NOT_STAFF_QUESTION", null, null); }
    private static Answer result(String code, String text, Instant now) { return new Answer(true, code, text, now); }
}
