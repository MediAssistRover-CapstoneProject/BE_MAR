package fu.se181738.backend_robot_v1.enums;

/** Values explicitly listed in docs/logicalDiagram.md. */
public enum ServiceRequestStatus {
    DRAFT,
    SUBMITTED,
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED,
    RECOVERY_REQUIRED
}
