package com.mediassistrover.backend.controller.verification;
import com.mediassistrover.backend.dto.verification.request.AuthVerificationRequests.Create;
import com.mediassistrover.backend.dto.verification.response.AuthVerificationResponse;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.AuthVerificationService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mediassistrover.backend.dto.verification.request.QrVerificationRequests.*;
import com.mediassistrover.backend.dto.verification.response.QrChallengeResponse;
import com.mediassistrover.backend.enums.AuthVerificationPurpose;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')") @Tag(name="Delivery Authentication")
public class AuthVerificationController {
    private final AuthVerificationService service; private final com.mediassistrover.backend.service.QrVerificationService qrService;
    public record StaffPhoneQr(UUID authJobStepId, AuthVerificationPurpose purpose, Instant expiresAt, List<String> rows) { }
    @io.swagger.v3.oas.annotations.Operation(summary="Issue a Staff confirmation grant", description="ADMIN or STAFF owning/assigned to the RUNNING Job. Purpose must match the AUTHENTICATE Step. Only STAFF_CONFIRMATION is supported here; QR_CODE requires a separate proof verifier. Same actor retry returns the existing unexpired grant without extending its TTL. Scope violations return 403.")
    @PostMapping("/jobs/{jobId}/auth-verifications") @ResponseStatus(HttpStatus.CREATED) public AuthVerificationResponse create(@PathVariable UUID jobId,@Valid @RequestBody Create body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.create(jobId,body,actor,RequestMetadataFactory.from(request));}
    @GetMapping("/auth-verifications/{id}") public AuthVerificationResponse get(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service.get(id,actor);}
    @Deprecated
    @io.swagger.v3.oas.annotations.Operation(summary="Inspect the active delivery QR challenge",deprecated=true,description="ADMIN support-only endpoint. Challenges are created automatically when the AUTHENTICATE Step starts. This endpoint never creates or extends a challenge.")
    @PostMapping("/jobs/{jobId}/qr-challenges") @PreAuthorize("hasRole('ADMIN')") public QrChallengeResponse issueQr(@PathVariable UUID jobId,@Valid @RequestBody Issue body,@AuthenticationPrincipal AccountPrincipal actor){return qrService.activeForAdmin(jobId,body,actor);}
    @PostMapping("/jobs/{jobId}/qr-verifications") public AuthVerificationResponse verifyQr(@PathVariable UUID jobId,@Valid @RequestBody Verify body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return qrService.verify(jobId,body.proof(),actor,RequestMetadataFactory.from(request));}

    @PostMapping("/jobs/{jobId}/staff-phone-qr")
    @PreAuthorize("hasRole('STAFF')")
    @io.swagger.v3.oas.annotations.Operation(summary="Show the assigned staff member's one-time QR for the robot camera",
            description="Requires the assigned staff and an active AUTHENTICATE challenge. QR display does not require camera streaming or a ready Pi scan session; the Pi session is validated when submitting the scan.")
    public ResponseEntity<StaffPhoneQr> staffPhoneQr(@PathVariable UUID jobId,
            @AuthenticationPrincipal AccountPrincipal actor) throws WriterException {
        var code = qrService.issueForStaffPhone(jobId, actor);
        var matrix = new QRCodeWriter().encode(code.proof(), BarcodeFormat.QR_CODE, 1, 1,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4));
        List<String> rows = new ArrayList<>(matrix.getHeight());
        for (int y = 0; y < matrix.getHeight(); y++) {
            StringBuilder row = new StringBuilder(matrix.getWidth());
            for (int x = 0; x < matrix.getWidth(); x++) row.append(matrix.get(x, y) ? '1' : '0');
            rows.add(row.toString());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new StaffPhoneQr(code.authJobStepId(), code.purpose(), code.expiresAt(), rows));
    }
}
