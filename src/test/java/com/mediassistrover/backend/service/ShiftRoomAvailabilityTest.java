package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.shift.request.ShiftRequests.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.impl.ShiftServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShiftRoomAvailabilityTest {
    @Test void createAndStartRejectRoomsWhoseMapWasDeactivated() {
        var rooms=mock(RoomRepository.class);var staff=mock(StaffProfileRepository.class);var shifts=mock(ShiftRepository.class);
        var service=mock(ShiftServiceImpl.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service,"roomRepository",rooms);ReflectionTestUtils.setField(service,"staffRepository",staff);
        ReflectionTestUtils.setField(service,"shiftRepository",shifts);
        var floor=Floor.builder().floorId(UUID.randomUUID()).active(true).build();
        var map=FacilityMap.builder().mapId(UUID.randomUUID()).floor(floor).status(MapStatus.INACTIVE).build();
        var room=Room.builder().roomId(UUID.randomUUID()).floor(floor).active(true).build();
        room.getEndpoints().add(Endpoint.builder().map(map).room(room).active(true).build());
        var person=StaffProfile.builder().staffProfileId(UUID.randomUUID()).active(true)
            .account(Account.builder().status(AccountStatus.ACTIVE).build()).build();
        when(rooms.findById(room.getRoomId())).thenReturn(Optional.of(room));
        when(staff.findById(person.getStaffProfileId())).thenReturn(Optional.of(person));
        var request=new Create(person.getStaffProfileId(),room.getRoomId(),"SHIFT_TEST","Morning",
            Instant.now().plusSeconds(60),Instant.now().plusSeconds(120),null,List.of());
        assertThrows(BusinessRuleException.class,()->service.create(request,UUID.randomUUID(),null));
        verify(shifts,never()).saveAndFlush(any());
        var shift=Shift.builder().shiftId(UUID.randomUUID()).status(ShiftStatus.PLANNED).room(room).build();
        when(shifts.findDetailedById(shift.getShiftId())).thenReturn(Optional.of(shift));
        assertThrows(BusinessRuleException.class,()->service.transition(shift.getShiftId(),new Transition(ShiftStatus.ACTIVE,null),UUID.randomUUID(),null));
        assertEquals(ShiftStatus.PLANNED,shift.getStatus());
        map.setStatus(MapStatus.ACTIVE);assertTrue(room.isOperational());
    }
}
