package com.mediassistrover.backend.simulation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/simulation")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class LocalSimulationController {
    private final LocalSimulationGateway gateway;
    @GetMapping("/state")
    public ObjectNode state(Principal user,@RequestParam(defaultValue="") String runId) {return gateway.status(user.getName(),runId);}
    @PostMapping("/commands") @ResponseStatus(HttpStatus.ACCEPTED)
    public ObjectNode command(Principal user,@RequestBody ObjectNode body) {return gateway.command(user.getName(),body);}
}
