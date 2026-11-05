package com.mediassistrover.backend.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DeletionContracts {
    private DeletionContracts() {}
    public record LifecycleActionRequest(@NotBlank @Size(min=5,max=500) String reason) {}
    public record DeletionImpactResponse(UUID resourceId,String resourceType,boolean canHardDelete,
                                         List<String> blockers,Map<String,Long> dependencies) {}
    public record HardDeleteResponse(UUID resourceId,String resourceType,Map<String,Long> deleted) {}
}
