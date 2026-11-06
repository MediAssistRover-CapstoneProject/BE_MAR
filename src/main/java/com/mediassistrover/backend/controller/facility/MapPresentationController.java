package com.mediassistrover.backend.controller.facility;

import com.mediassistrover.backend.dto.facility.MapPresentationContracts.*;
import com.mediassistrover.backend.service.MapPresentationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/maps/{mapId}/presentation")
@PreAuthorize("hasRole('ADMIN')")
public class MapPresentationController {
    private final MapPresentationService service;
    @GetMapping public ResponseEntity<Editor> editor(@PathVariable UUID mapId) { return fresh(service.editor(mapId)); }
    @PutMapping public ResponseEntity<Editor> save(@PathVariable UUID mapId,@Valid @RequestBody Save request) { return fresh(service.save(mapId,request)); }
    @PostMapping("/publish") public ResponseEntity<Editor> publish(@PathVariable UUID mapId,@Valid @RequestBody Change request) { return fresh(service.publish(mapId,request)); }
    @PostMapping("/unpublish") public ResponseEntity<Editor> unpublish(@PathVariable UUID mapId,@Valid @RequestBody Change request) { return fresh(service.unpublish(mapId,request)); }
    @GetMapping("/published") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<Published> published(@PathVariable UUID mapId) { return fresh(service.published(mapId)); }
    private <T> ResponseEntity<T> fresh(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
