package com.mediassistrover.backend.controller.staff;
import com.mediassistrover.backend.service.StaffDutyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
import com.mediassistrover.backend.dto.common.PageResponse;
import jakarta.validation.constraints.*;
import java.util.*;
@RestController @RequiredArgsConstructor @org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1/staff/on-duty") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class StaffDutyController {
    private final StaffDutyService duty;
    @GetMapping public PageResponse<StaffDutyService.Duty> list(@RequestParam(required=false)UUID mapId,@RequestParam(required=false)UUID roomId,
        @RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size){
        var all=duty.list(mapId,roomId);int start=(int)Math.min((long)page*size,all.size());
        return PageResponse.from(new PageImpl<>(all.subList(start,Math.min(start+size,all.size())),PageRequest.of(page,size),all.size()));}
}
