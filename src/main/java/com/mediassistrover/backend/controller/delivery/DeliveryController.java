package com.mediassistrover.backend.controller.delivery;
import com.mediassistrover.backend.dto.delivery.request.DeliveryRequests.*;
import com.mediassistrover.backend.dto.delivery.response.DeliveryResponses.DeliveryResponse;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.DeliveryService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/delivery-requests") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')") @Tag(name="Delivery Requests")
public class DeliveryController {
    private final DeliveryService service;
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public DeliveryResponse create(@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody Create body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.create(body,key,actor,RequestMetadataFactory.from(request));}
    @GetMapping("/{id}") public DeliveryResponse get(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service.get(id,actor);}
    @PostMapping("/{id}/confirm-pickup") public DeliveryResponse pickup(@PathVariable UUID id,@Valid @RequestBody Confirm body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.confirmPickup(id,body,actor,RequestMetadataFactory.from(request));}
    @PostMapping("/{id}/confirm-delivery") public DeliveryResponse delivery(@PathVariable UUID id,@Valid @RequestBody Confirm body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.confirmDelivery(id,body,actor,RequestMetadataFactory.from(request));}
}
