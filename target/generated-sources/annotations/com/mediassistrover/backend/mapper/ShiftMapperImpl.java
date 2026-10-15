package com.mediassistrover.backend.mapper;

import com.mediassistrover.backend.dto.shift.request.ShiftRequests;
import com.mediassistrover.backend.dto.shift.response.ShiftResponses;
import com.mediassistrover.backend.entity.Room;
import com.mediassistrover.backend.entity.Shift;
import com.mediassistrover.backend.entity.Slot;
import com.mediassistrover.backend.entity.StaffProfile;
import com.mediassistrover.backend.enums.ShiftStatus;
import com.mediassistrover.backend.enums.SlotStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-13T11:40:03+0700",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class ShiftMapperImpl implements ShiftMapper {

    @Override
    public ShiftResponses.ShiftResponse toResponse(Shift entity) {
        if ( entity == null ) {
            return null;
        }

        UUID staffProfileId = null;
        String staffCode = null;
        UUID roomId = null;
        String roomCode = null;
        UUID shiftId = null;
        String shiftCode = null;
        String shiftName = null;
        Instant startsAt = null;
        Instant endsAt = null;
        ShiftStatus status = null;
        String note = null;
        List<ShiftResponses.SlotResponse> slots = null;
        Instant createdAt = null;
        Instant updatedAt = null;

        staffProfileId = entityStaffProfileStaffProfileId( entity );
        staffCode = entityStaffProfileStaffCode( entity );
        roomId = entityRoomRoomId( entity );
        roomCode = entityRoomRoomCode( entity );
        shiftId = entity.getShiftId();
        shiftCode = entity.getShiftCode();
        shiftName = entity.getShiftName();
        startsAt = entity.getStartsAt();
        endsAt = entity.getEndsAt();
        status = entity.getStatus();
        note = entity.getNote();
        slots = slotListToSlotResponseList( entity.getSlots() );
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        ShiftResponses.ShiftResponse shiftResponse = new ShiftResponses.ShiftResponse( shiftId, staffProfileId, staffCode, roomId, roomCode, shiftCode, shiftName, startsAt, endsAt, status, note, slots, createdAt, updatedAt );

        return shiftResponse;
    }

    @Override
    public ShiftResponses.SlotResponse toResponse(Slot entity) {
        if ( entity == null ) {
            return null;
        }

        UUID slotId = null;
        int sequenceNo = 0;
        Instant startsAt = null;
        Instant endsAt = null;
        SlotStatus status = null;
        String note = null;
        Instant createdAt = null;

        slotId = entity.getSlotId();
        sequenceNo = entity.getSequenceNo();
        startsAt = entity.getStartsAt();
        endsAt = entity.getEndsAt();
        status = entity.getStatus();
        note = entity.getNote();
        createdAt = entity.getCreatedAt();

        ShiftResponses.SlotResponse slotResponse = new ShiftResponses.SlotResponse( slotId, sequenceNo, startsAt, endsAt, status, note, createdAt );

        return slotResponse;
    }

    @Override
    public Shift toEntity(ShiftRequests.Create request) {
        if ( request == null ) {
            return null;
        }

        Shift.ShiftBuilder shift = Shift.builder();

        shift.shiftCode( request.shiftCode() );
        shift.shiftName( request.shiftName() );
        shift.startsAt( request.startsAt() );
        shift.endsAt( request.endsAt() );
        shift.note( request.note() );

        shift.status( ShiftStatus.PLANNED );

        return shift.build();
    }

    @Override
    public Slot toEntity(ShiftRequests.SlotInput request) {
        if ( request == null ) {
            return null;
        }

        Slot.SlotBuilder slot = Slot.builder();

        slot.sequenceNo( request.sequenceNo() );
        slot.startsAt( request.startsAt() );
        slot.endsAt( request.endsAt() );
        slot.status( request.status() );
        slot.note( request.note() );

        return slot.build();
    }

    @Override
    public Slot toEntity(ShiftRequests.SlotCreate request) {
        if ( request == null ) {
            return null;
        }

        Slot.SlotBuilder slot = Slot.builder();

        slot.sequenceNo( request.sequenceNo() );
        slot.startsAt( request.startsAt() );
        slot.endsAt( request.endsAt() );
        slot.status( request.status() );
        slot.note( request.note() );

        return slot.build();
    }

    private UUID entityStaffProfileStaffProfileId(Shift shift) {
        StaffProfile staffProfile = shift.getStaffProfile();
        if ( staffProfile == null ) {
            return null;
        }
        return staffProfile.getStaffProfileId();
    }

    private String entityStaffProfileStaffCode(Shift shift) {
        StaffProfile staffProfile = shift.getStaffProfile();
        if ( staffProfile == null ) {
            return null;
        }
        return staffProfile.getStaffCode();
    }

    private UUID entityRoomRoomId(Shift shift) {
        Room room = shift.getRoom();
        if ( room == null ) {
            return null;
        }
        return room.getRoomId();
    }

    private String entityRoomRoomCode(Shift shift) {
        Room room = shift.getRoom();
        if ( room == null ) {
            return null;
        }
        return room.getRoomCode();
    }

    protected List<ShiftResponses.SlotResponse> slotListToSlotResponseList(List<Slot> list) {
        if ( list == null ) {
            return null;
        }

        List<ShiftResponses.SlotResponse> list1 = new ArrayList<ShiftResponses.SlotResponse>( list.size() );
        for ( Slot slot : list ) {
            list1.add( toResponse( slot ) );
        }

        return list1;
    }
}
