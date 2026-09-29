package fu.se181738.backend_robot_v1.enums;

/** Values explicitly listed in docs/logicalDiagram.md. */
public enum JobStepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    RETRYING
}
