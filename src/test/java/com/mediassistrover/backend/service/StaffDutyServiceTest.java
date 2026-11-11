package com.mediassistrover.backend.service;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StaffDutyServiceTest {
 final ShiftRepository shifts=mock(ShiftRepository.class);final EndpointRepository endpoints=mock(EndpointRepository.class);
 final FacilityMapRepository maps=mock(FacilityMapRepository.class);final EndpointGroupMemberRepository members=mock(EndpointGroupMemberRepository.class);
 final StaffMeService me=mock(StaffMeService.class);final Instant now=Instant.parse("2026-11-10T01:00:00Z");
 final StaffDutyService service=new StaffDutyService(shifts,endpoints,maps,members,me,Clock.fixed(now,ZoneOffset.UTC));
 Account a;Shift shift;AccountPrincipal actor;UUID map=UUID.randomUUID();
 @BeforeEach void setup(){a=Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).status(AccountStatus.ACTIVE).build();actor=AccountPrincipal.fromAccount(a);
 shift=Shift.builder().shiftId(UUID.randomUUID()).staffProfile(StaffProfile.builder().account(a).active(true).build()).room(Room.builder().roomId(UUID.randomUUID()).active(true).build())
 .startsAt(now.minusSeconds(60)).endsAt(now.plusSeconds(60)).status(ShiftStatus.PLANNED).build();when(shifts.lockAttendance(shift.getShiftId())).thenReturn(Optional.of(shift));}
 @Test void checkInIsIdempotentAndCheckoutRemovesEligibility(){service.attendance(shift.getShiftId(),actor,true);service.attendance(shift.getShiftId(),actor,true);
 assertEquals(now,shift.getCheckedInAt());assertEquals(ShiftStatus.ACTIVE,shift.getStatus());assertSame(shift,service.require(shift.getShiftId(),a.getAccountId()));
 shift.setEndsAt(now);
 service.attendance(shift.getShiftId(),actor,false);service.attendance(shift.getShiftId(),actor,false);assertEquals(now,shift.getCheckedOutAt());
 assertThrows(BusinessRuleException.class,()->service.require(shift.getShiftId(),a.getAccountId()));assertThrows(BusinessRuleException.class,()->service.attendance(shift.getShiftId(),actor,true));}
 @Test void rejectsUnattendedAndExpiredShifts(){shift.setStatus(ShiftStatus.ACTIVE);assertThrows(BusinessRuleException.class,()->service.require(shift.getShiftId(),a.getAccountId()));
 shift.setCheckedInAt(now.minusSeconds(30));shift.setEndsAt(now);assertThrows(BusinessRuleException.class,()->service.require(shift.getShiftId(),a.getAccountId()));assertThrows(BusinessRuleException.class,()->service.attendance(shift.getShiftId(),actor,true));}
 @Test void rejectsOtherStaffAndCancelledShift(){var other=AccountPrincipal.fromAccount(Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).status(AccountStatus.ACTIVE).build());
 assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.attendance(shift.getShiftId(),other,true));shift.setStatus(ShiftStatus.CANCELLED);assertThrows(BusinessRuleException.class,()->service.attendance(shift.getShiftId(),actor,true));}
 Endpoint endpoint(){return Endpoint.builder().endpointId(UUID.randomUUID()).map(FacilityMap.builder().mapId(map).build()).room(shift.getRoom()).active(true).accessible(true).build();}
 @Test void refusesAmbiguousOrMissingRoomEndpoints(){when(endpoints.findByMapMapIdOrderByEndpointCodeAsc(map)).thenReturn(List.of());assertThrows(BusinessRuleException.class,()->service.target(shift,map));
 var first=endpoint();when(endpoints.findByMapMapIdOrderByEndpointCodeAsc(map)).thenReturn(List.of(first));assertSame(first,service.target(shift,map));
 when(endpoints.findByMapMapIdOrderByEndpointCodeAsc(map)).thenReturn(List.of(first,endpoint()));assertThrows(BusinessRuleException.class,()->service.target(shift,map));}
 @Test void resolvesFirstUsableDefaultGroupMember(){var group=EndpointGroup.builder().endpointGroupId(UUID.randomUUID()).active(true).map(FacilityMap.builder().mapId(map).build()).build();shift.getRoom().setDefaultEndpointGroup(group);
 var first=endpoint();first.setAccessible(false);var second=endpoint();when(members.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(group.getEndpointGroupId())).thenReturn(List.of(EndpointGroupMember.builder().active(true).endpoint(first).build(),EndpointGroupMember.builder().active(true).endpoint(second).build()));assertSame(second,service.target(shift,map));}
}
