package com.mediassistrover.backend.controller.staff;
import com.mediassistrover.backend.service.FaceAuthService;
import com.mediassistrover.backend.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.UUID;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class StaffFaceController {
    private final FaceAuthService face;
    public record Review(UUID avatarVersion,boolean approve) {}
    @GetMapping("/staff/{accountId}/face-status") @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public FaceAuthService.Status status(@PathVariable UUID accountId){return face.status(accountId);}
    @PostMapping("/admin/staff/{accountId}/face-review") @PreAuthorize("hasRole('ADMIN')")
    public FaceAuthService.Status review(@PathVariable UUID accountId,@RequestBody Review request,@AuthenticationPrincipal AccountPrincipal p){
        return face.review(accountId,request.avatarVersion(),request.approve(),p);
    }
}
