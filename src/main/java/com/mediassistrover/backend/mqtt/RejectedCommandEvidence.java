package com.mediassistrover.backend.mqtt;

import com.mediassistrover.backend.entity.RobotCommandOutbox;
import com.mediassistrover.backend.entity.RobotMqttInbox;

/** Only an already authenticated, committed negative ACK proves non-acceptance. */
final class RejectedCommandEvidence {
    static boolean matches(RobotMqttInbox inbox, RobotCommandOutbox command) {
        var e=inbox.getRawEnvelope();
        if(!Boolean.TRUE.equals(inbox.getAccepted()) || !"COMMAND_ACK".equals(inbox.getMessageType())
                || !command.getRobotId().equals(inbox.getRobotId()) || e==null) return false;
        var p=e.path("payload");
        return "COMMAND_ACK".equals(e.path("messageType").asText())
            && command.getCommandId().toString().equals(e.path("correlationId").asText())
            && command.getJobId().toString().equals(e.path("jobId").asText())
            && command.getJobStepId().toString().equals(e.path("jobStepId").asText())
            && command.getOperationKey().equals(e.path("operationKey").asText())
            && command.getCommandId().toString().equals(p.path("commandId").asText())
            && "EXECUTE_STEP".equals(p.path("commandType").asText())
            && p.path("accepted").isBoolean() && !p.path("accepted").booleanValue()
            && !p.path("replayed").asBoolean()
            && "LOCALIZATION_NOT_READY".equals(p.path("errorCode").asText());
    }
}
