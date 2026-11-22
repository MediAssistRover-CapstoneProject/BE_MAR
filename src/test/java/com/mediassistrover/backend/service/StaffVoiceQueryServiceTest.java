package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffVoiceQueryServiceTest {
    private final Instant now = Instant.parse("2026-11-22T06:00:00Z");
    private final StaffProfileRepository profiles = mock(StaffProfileRepository.class);
    private final StaffDutyService duty = mock(StaffDutyService.class);
    private final FacilityMapRepository maps = mock(FacilityMapRepository.class);
    private final EndpointRepository endpoints = mock(EndpointRepository.class);
    private final NavigationSessionService sessions = mock(NavigationSessionService.class);
    private final StaffVoiceQueryService service = new StaffVoiceQueryService(
            profiles, duty, maps, endpoints, sessions, Clock.fixed(now, ZoneOffset.UTC));

    private StaffProfile doctor(String name, AccountStatus status) {
        Account account = Account.builder().accountId(UUID.randomUUID()).fullName(name)
                .role(Role.STAFF).status(status).build();
        return StaffProfile.builder().staffCode("DOC01").jobTitle("Bác sĩ")
                .active(true).account(account).build();
    }

    private StaffDutyService.Duty checkedIn(StaffProfile profile, UUID roomId, String roomName) {
        return new StaffDutyService.Duty(profile.getStaffProfileId(), profile.getAccount().getAccountId(),
                profile.getAccount().getFullName(), null, UUID.randomUUID(), now.minusSeconds(60),
                now.plusSeconds(3600), now.minusSeconds(30), roomId, "XQ", roomName,
                null, false, null, null);
    }

    @Test void namedDoctorUsesCurrentCheckedInShift() {
        StaffProfile doctor = doctor("Nguyễn Thanh", AccountStatus.ACTIVE);
        when(profiles.findVoiceDirectory()).thenReturn(List.of(doctor));
        when(duty.list(null, null)).thenReturn(List.of(checkedIn(doctor, UUID.randomUUID(), "Phòng x quang")));
        var answer = service.answer("Bác sĩ Nguyễn Thanh đang ở phòng nào?", null);
        assertEquals("DOCTOR_ON_DUTY", answer.code());
        assertTrue(answer.answer().contains("Phòng x quang"));
    }

    @Test void offDutyUnknownAndLockedHaveDifferentSafeAnswers() {
        StaffProfile doctor = doctor("Nguyễn Thanh", AccountStatus.ACTIVE);
        when(profiles.findVoiceDirectory()).thenReturn(List.of(doctor));
        when(duty.list(null, null)).thenReturn(List.of());
        assertEquals("DOCTOR_OFF_DUTY", service.answer("Bác sĩ Nguyễn Thanh đang ở đâu?", null).code());
        assertEquals("DOCTOR_NOT_FOUND", service.answer("Bác sĩ An đang ở đâu?", null).code());
        doctor.getAccount().setStatus(AccountStatus.LOCKED);
        var locked = service.answer("Bác sĩ Nguyễn Thanh đang ở đâu?", null);
        assertEquals("DOCTOR_NOT_FOUND", locked.code());
        assertFalse(locked.answer().toLowerCase(Locale.ROOT).contains("khóa"));
        doctor.getAccount().setStatus(AccountStatus.ACTIVE);
        doctor.getAccount().setLockedUntil(now.plusSeconds(300));
        assertEquals("DOCTOR_NOT_FOUND", service.answer("Bác sĩ Nguyễn Thanh đang ở đâu?", null).code());
        assertEquals("NAME_REQUIRED", service.answer("Bác sĩ đó đang ở phòng nào?", null).code());
        doctor.getAccount().setLockedUntil(null);
        doctor.setJobTitle("Điều dưỡng");
        assertEquals("STAFF_NOT_DOCTOR", service.answer("Bác sĩ Nguyễn Thanh đang ở đâu?", null).code());
        when(duty.list(null, null)).thenReturn(List.of(checkedIn(doctor, UUID.randomUUID(), "Phòng lễ tân")));
        var working = service.answer("Bác sĩ Nguyễn Thanh đang ở đâu?", null);
        assertEquals("STAFF_ON_DUTY_NOT_DOCTOR", working.code());
        assertTrue(working.answer().contains("Phòng lễ tân"));
    }

    @Test void roomQueryListsOnlyCheckedInDoctorsOnActiveMap() {
        UUID mapId = UUID.randomUUID(), roomId = UUID.randomUUID();
        FacilityMap map = mock(FacilityMap.class);
        Room room = mock(Room.class);
        Endpoint endpoint = mock(Endpoint.class);
        StaffProfile doctor = doctor("Nguyễn Thanh", AccountStatus.ACTIVE);
        when(map.getStatus()).thenReturn(MapStatus.ACTIVE);
        when(room.getRoomName()).thenReturn("Phòng x quang");
        when(room.getRoomId()).thenReturn(roomId);
        when(room.isOperationalOn(map)).thenReturn(true);
        when(endpoint.isActive()).thenReturn(true);
        when(endpoint.getRoom()).thenReturn(room);
        when(maps.findById(mapId)).thenReturn(Optional.of(map));
        when(endpoints.findByMapMapIdOrderByEndpointCodeAsc(mapId)).thenReturn(List.of(endpoint));
        when(profiles.findVoiceDirectory()).thenReturn(List.of(doctor));
        when(duty.list(mapId, roomId)).thenReturn(List.of(checkedIn(doctor, roomId, "Phòng x quang")));
        assertEquals("ROOM_DOCTORS", service.answer("Phòng x quang bác sĩ nào đang trực?", mapId).code());
        assertEquals("ROOM_DOCTORS", service.answer("Phòng x quang bác sĩ nào đg trực?", mapId).code());
        when(duty.list(mapId, roomId)).thenReturn(List.of());
        assertEquals("ROOM_NO_DOCTOR", service.answer("Bác sĩ nào đang trực ở phòng x quang?", mapId).code());
        doctor.setJobTitle("lao cong");
        when(duty.list(mapId, roomId)).thenReturn(List.of(checkedIn(doctor, roomId, "Phòng x quang")));
        var staff = service.answer("Phòng x quang bác sĩ nào đang trực?", mapId);
        assertEquals("ROOM_STAFF_NO_DOCTOR", staff.code());
        assertTrue(staff.answer().contains("Nguyễn Thanh"));
        assertTrue(staff.answer().contains("chưa xác nhận"));
    }

    @Test void unrelatedLocationQuestionIsLeftForKnowledgeAndNavigation() {
        assertFalse(service.answer("Xe lăn ở đâu?", UUID.randomUUID()).recognized());
        assertFalse(service.answer("Phòng x quang ở đâu?", UUID.randomUUID()).recognized());
    }
}
