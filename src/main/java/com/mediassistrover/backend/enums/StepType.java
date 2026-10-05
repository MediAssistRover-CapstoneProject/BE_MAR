package com.mediassistrover.backend.enums;

/** Fixed protocol handlers. Business actions are configured in Step.paramsJson. */
public enum StepType {
    MOVE("Di chuyển", StepExecutorKind.PI),
    INTERACT("Tương tác", StepExecutorKind.SERVER),
    DATA_SERVICE("Dịch vụ dữ liệu", StepExecutorKind.SERVER),
    AUTHENTICATE("Xác thực", StepExecutorKind.SERVER),
    CONTROL_HARDWARE("Điều khiển phần cứng", StepExecutorKind.PI),
    HANDLE_PAYLOAD("Xử lý vật phẩm", StepExecutorKind.SERVER),
    EXECUTE_SENSING("Thu thập cảm biến", StepExecutorKind.MIXED),
    NOTIFY("Thông báo", StepExecutorKind.SERVER),
    WAIT("Chờ", StepExecutorKind.MIXED),
    CHECK_CONDITION("Kiểm tra điều kiện", StepExecutorKind.PI),
    SET_CONTEXT("Gán context", StepExecutorKind.PI);

    private final String displayName;
    private final StepExecutorKind executorKind;
    StepType(String displayName, StepExecutorKind executorKind) {
        this.displayName = displayName;
        this.executorKind = executorKind;
    }
    public String getStepTypeCode() { return name(); }
    public String getDisplayName() { return displayName; }
    public StepExecutorKind getExecutorKind() { return executorKind; }
}
