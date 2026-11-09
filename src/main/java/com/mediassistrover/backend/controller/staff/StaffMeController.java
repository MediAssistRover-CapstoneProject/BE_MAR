package com.mediassistrover.backend.controller.staff;

import com.mediassistrover.backend.config.OpenApiConfig;
import com.mediassistrover.backend.dto.staff.response.StaffMeResponse;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.StaffMeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STAFF')")
@Tag(name = "Staff Profile", description = "Thông tin của Staff đang đăng nhập")
@SecurityRequirement(name = OpenApiConfig.USER_BEARER_SCHEME)
public class StaffMeController {
    private final StaffMeService service;

    @org.springframework.web.bind.annotation.PatchMapping("/me")
    public StaffMeResponse update(@AuthenticationPrincipal AccountPrincipal principal,@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody StaffMeService.Update input){return service.update(principal,input);}
    @org.springframework.web.bind.annotation.PostMapping(value="/me/avatar",consumes="multipart/form-data")
    public StaffMeResponse avatar(@AuthenticationPrincipal AccountPrincipal principal,@org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file){return service.avatar(principal,file);}
    @org.springframework.web.bind.annotation.DeleteMapping("/me/avatar")
    public StaffMeResponse clearAvatar(@AuthenticationPrincipal AccountPrincipal principal){return service.avatar(principal,null);}
    @GetMapping("/me")
    @Operation(summary = "Lấy Account, StaffProfile và ca trực đang hiệu lực của Staff hiện tại")
    public StaffMeResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.get(principal);
    }
}
