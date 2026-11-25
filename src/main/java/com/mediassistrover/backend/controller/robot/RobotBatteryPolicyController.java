package com.mediassistrover.backend.controller.robot;

import com.mediassistrover.backend.service.RobotBatteryPolicyService;
import com.mediassistrover.backend.service.RobotBatteryPolicyService.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class RobotBatteryPolicyController {
    private final RobotBatteryPolicyService policy;
    @GetMapping("/robots/{id}/battery-policy")
    public Status status(@PathVariable UUID id,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return policy.current(id);}
    @GetMapping("/robots/{id}/chargers") public List<Charger> chargers(@PathVariable UUID id){return policy.chargers(id);}
    @PostMapping("/robots/{id}/return-to-charger") @PreAuthorize("hasRole('ADMIN')")
    public Status request(@PathVariable UUID id,@RequestBody(required=false) ReturnRequest body,
                          @AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        policy.requestReturn(id,body,actor.accountId(),RequestMetadataFactory.from(request));policy.advance(id);return policy.current(id);
    }
    @PostMapping("/admin/robots/{id}/battery-policy/test") @PreAuthorize("hasRole('ADMIN')")
    public Status test(@PathVariable UUID id,@Valid @RequestBody TestRequest body,
                       @AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        Status result=policy.test(id,body,actor.accountId(),RequestMetadataFactory.from(request));
        if(result.dryRun())return result;
        policy.advance(id);return policy.current(id);
    }
    @DeleteMapping("/admin/robots/{id}/battery-policy/test") @PreAuthorize("hasRole('ADMIN')")
    public Status endTest(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        policy.endTest(id,actor.accountId(),RequestMetadataFactory.from(request));policy.advance(id);return policy.current(id);
    }
}
