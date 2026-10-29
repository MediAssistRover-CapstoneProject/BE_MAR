package com.mediassistrover.backend.dto.job.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record BulkRecoveryRequest(
        @NotNull Boolean dryRun,
        @NotEmpty @Size(max=50) List<@Valid @NotNull Item> operations) {
    public record Item(@NotNull UUID jobId, @NotNull UUID jobStepId,
                       @NotBlank @Pattern(regexp="[A-Za-z0-9._:-]{1,150}") String idempotencyKey,
                       @NotNull @Valid MqttOperationRecoveryRequest recovery) {}
}
