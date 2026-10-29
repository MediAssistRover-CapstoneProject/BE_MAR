package com.mediassistrover.backend.dto.job.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CloseRejectedJobRequest(@NotNull UUID commandId,
        @NotBlank @Size(max=150) String operationKey,
        @NotBlank @Size(max=2000) String note) {}
