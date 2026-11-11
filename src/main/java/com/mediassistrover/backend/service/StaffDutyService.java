package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class StaffDutyService {
    private final ShiftRepository shifts;
    private final EndpointRepository endpoints;
    private final FacilityMapRepository maps;
    private final EndpointGroupMemberRepository members;
    private final StaffMeService me;
    private final Clock clock;
    public record Duty(UUID staffProfileId, UUID accountId, String fullName, String avatarUrl,
        UUID shiftId, Instant startsAt, Instant endsAt, Instant checkedInAt, UUID roomId,
        String roomCode, String roomName, UUID endpointId, boolean deliverable, String reason) {}
    @Transactional public Map<String,Object> attendance(UUID id, AccountPrincipal actor, boolean checkIn) {
        me.requireActiveProfile(actor);
        Shift s=shifts.lockAttendance(id).orElseThrow(()->new ResourceNotFoundException("Shift not found"));
        if(!s.getStaffProfile().getAccount().getAccountId().equals(actor.accountId()))
            throw new org.springframework.security.access.AccessDeniedException("This shift belongs to another staff member");
        Instant now=clock.instant();
        if(checkIn) {
            if(s.getCheckedOutAt()!=null)throw new BusinessRuleException("Shift already checked out");
            if(s.getStatus()==ShiftStatus.CANCELLED||s.getStatus()==ShiftStatus.COMPLETED||now.isBefore(s.getStartsAt())||!now.isBefore(s.getEndsAt()))
                throw new BusinessRuleException("Check-in is only allowed during the assigned shift");
            if(s.getCheckedInAt()==null)s.setCheckedInAt(now);
            s.setStatus(ShiftStatus.ACTIVE);
        } else {
            if(s.getCheckedInAt()==null)throw new BusinessRuleException("Check in before checking out");
            if(s.getCheckedOutAt()==null) {
                if(now.isBefore(s.getEndsAt()))
                    throw new BusinessRuleException("Checkout is only allowed after the assigned shift ends");
                s.setCheckedOutAt(now);
            }
        }
        var out=new LinkedHashMap<String,Object>();out.put("shiftId",id);out.put("checkedInAt",s.getCheckedInAt());out.put("checkedOutAt",s.getCheckedOutAt());return out;
    }
    public Shift require(UUID id, UUID account) {
        Shift s=shifts.lockAttendance(id).orElseThrow(()->new ResourceNotFoundException("Receiver shift not found"));
        if(!activeReceiver(s,account,clock.instant()))
            throw new BusinessRuleException("Receiver is no longer checked in to this shift");
        return s;
    }

    public boolean receiverStillOnDuty(UUID shiftId, UUID accountId, UUID roomId) {
        return shifts.lockAttendance(shiftId).filter(shift -> activeReceiver(shift,accountId,clock.instant()))
                .filter(shift -> shift.getRoom().getRoomId().equals(roomId)).isPresent();
    }

    private boolean activeReceiver(Shift shift, UUID accountId, Instant now) {
        var profile=shift.getStaffProfile();
        var account=profile.getAccount();
        return account.getAccountId().equals(accountId) && profile.isActive()
                && account.getStatus()==AccountStatus.ACTIVE && account.getRole()==Role.STAFF && !account.isMustChangePassword()
                && shift.getStatus()==ShiftStatus.ACTIVE && shift.getCheckedInAt()!=null && shift.getCheckedOutAt()==null
                && !now.isBefore(shift.getStartsAt()) && now.isBefore(shift.getEndsAt()) && shift.getRoom().isActive();
    }
    public Endpoint target(Shift s, UUID map) {
        Room room=s.getRoom();var group=room.getDefaultEndpointGroup();
        if(group!=null) {
            if(!group.isActive()||!group.getMap().getMapId().equals(map))throw new BusinessRuleException("Configure an active room endpoint group on this map");
            return members.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(group.getEndpointGroupId()).stream()
                .filter(EndpointGroupMember::isActive).map(EndpointGroupMember::getEndpoint)
                .filter(e->usable(e,room,map)).findFirst().orElseThrow(()->new BusinessRuleException("Room has no usable endpoint"));
        }
        var candidates=endpoints.findByMapMapIdOrderByEndpointCodeAsc(map).stream().filter(e->usable(e,room,map)).toList();
        if(candidates.size()!=1)throw new BusinessRuleException("Configure a unique endpoint or default endpoint group for the room");
        return candidates.getFirst();
    }
    private boolean usable(Endpoint e, Room r, UUID map){return e.isActive()&&e.isAccessible()&&e.getRoom()!=null&&e.getRoom().getRoomId().equals(r.getRoomId())&&e.getMap().getMapId().equals(map);}
    public List<Duty> list(UUID map,UUID room){
        FacilityMap selected=map==null?null:maps.findById(map).orElseThrow(()->new ResourceNotFoundException("Map not found"));
        return shifts.findOnDuty(clock.instant()).stream()
        .filter(s->room==null||room.equals(s.getRoom().getRoomId()))
        .filter(s->selected==null||selected.getFloor().getFloorId().equals(s.getRoom().getFloor().getFloorId()))
        .map(s->{Endpoint e=null;String reason=null;try{if(map!=null)e=target(s,map);else reason="Select a map";}catch(BusinessRuleException ex){reason=ex.getMessage();}
            var p=s.getStaffProfile();var a=p.getAccount();return new Duty(p.getStaffProfileId(),a.getAccountId(),a.getFullName(),p.getUrl(),s.getShiftId(),s.getStartsAt(),s.getEndsAt(),s.getCheckedInAt(),s.getRoom().getRoomId(),s.getRoom().getRoomCode(),s.getRoom().getRoomName(),e==null?null:e.getEndpointId(),e!=null,reason);}).toList();}
}
