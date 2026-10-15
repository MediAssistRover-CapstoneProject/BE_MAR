package com.mediassistrover.backend.controller.facility;

import com.mediassistrover.backend.dto.common.PageResponse;
import com.mediassistrover.backend.dto.facility.response.FacilityResponses.*;
import com.mediassistrover.backend.service.FacilityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Validated
@Tag(name = "Public Facility Catalog", description = "Chỉ trả dữ liệu active/public")
public class PublicFacilityController {
    private final FacilityService service;
    @GetMapping("/hospital-information") public PageResponse<HospitalInformationResponse> hospitals(@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size){return PageResponse.from(service.listPublicHospitalInformation(PageRequest.of(page,size,Sort.by("createdAt"))));}
    @GetMapping("/floors") public PageResponse<FloorResponse> floors(@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size){return PageResponse.from(service.listPublicFloors(PageRequest.of(page,size,Sort.by("displayOrder"))));}
    @GetMapping("/zones") public PageResponse<ZoneResponse> zones(@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size){return PageResponse.from(service.listPublicZones(PageRequest.of(page,size,Sort.by("createdAt"))));}
    @GetMapping("/rooms") public PageResponse<RoomResponse> rooms(@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size){return PageResponse.from(service.listPublicRooms(PageRequest.of(page,size,Sort.by("createdAt"))));}
    @GetMapping("/endpoints") public PageResponse<EndpointResponse> endpoints(@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size){return PageResponse.from(service.listPublicEndpoints(PageRequest.of(page,size,Sort.by("createdAt"))));}
}
