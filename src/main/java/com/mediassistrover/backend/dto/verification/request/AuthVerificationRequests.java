package com.mediassistrover.backend.dto.verification.request;
import com.mediassistrover.backend.enums.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
public final class AuthVerificationRequests {
    private AuthVerificationRequests() { }
    public record Create(@NotNull UUID authJobStepId,@NotNull AuthVerificationMethod method,
                         @NotNull AuthVerificationPurpose purpose,@Min(30) @Max(900) int validForSeconds) { }
}
