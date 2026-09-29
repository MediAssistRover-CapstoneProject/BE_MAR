package fu.se181738.backend_robot_v1.enums;

/** Values explicitly listed in docs/logicalDiagram.md. */
public enum JobStatus {
    QUEUED,
    ACCEPTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    RECOVERY_REQUIRED
}
