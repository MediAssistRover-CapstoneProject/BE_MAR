package com.mediassistrover.backend.dto.job.request;
import jakarta.validation.constraints.*;
import java.util.UUID;
public final class JobRequests {
    private JobRequests() {}
    public record CancelJobRequest(@NotBlank @Size(max=1000) String reason) {}
    public record DeliveryRecoveryConfirmation(@NotNull @AssertTrue Boolean cargoRemoved,
            @NotNull @AssertTrue Boolean compartmentClosed,@NotNull @AssertTrue Boolean compartmentLocked) {}
    public record ReconcileJobRequest(@NotBlank @Size(max=2000) String note,@NotNull ReconcileOutcome outcome,
            @jakarta.validation.Valid DeliveryRecoveryConfirmation deliveryRecovery) {
        public ReconcileJobRequest(String note,ReconcileOutcome outcome){this(note,outcome,null);}
    }
    public record AssignRobotRequest(@NotNull UUID robotId) {}
    public record AssignStaffRequest(@NotNull UUID staffAccountId) {}
    public enum ReconcileOutcome { RESUME_AS_FAILED, CONFIRM_CANCELLED }
}
