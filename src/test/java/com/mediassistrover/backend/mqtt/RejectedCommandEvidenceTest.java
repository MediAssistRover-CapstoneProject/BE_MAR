package com.mediassistrover.backend.mqtt;

import com.mediassistrover.backend.entity.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RejectedCommandEvidenceTest {
    @Test void onlyCommittedMatchingExplicitRejectionIsEvidence() {
        var command=RobotCommandOutbox.builder().commandId(UUID.randomUUID()).robotId(UUID.randomUUID())
            .jobId(UUID.randomUUID()).jobStepId(UUID.randomUUID()).operationKey("op:1").build();
        var envelope=new MqttWire().object().put("messageType","COMMAND_ACK")
            .put("correlationId",command.getCommandId().toString()).put("jobId",command.getJobId().toString())
            .put("jobStepId",command.getJobStepId().toString()).put("operationKey","op:1");
        var payload=envelope.putObject("payload").put("commandId",command.getCommandId().toString())
            .put("commandType","EXECUTE_STEP").put("accepted",false).put("errorCode","LOCALIZATION_NOT_READY");
        var inbox=new RobotMqttInbox();inbox.setAccepted(true);inbox.setRobotId(command.getRobotId());
        inbox.setMessageType("COMMAND_ACK");inbox.setRawEnvelope(envelope);
        assertTrue(RejectedCommandEvidence.matches(inbox,command));
        inbox.setAccepted(false);assertFalse(RejectedCommandEvidence.matches(inbox,command));inbox.setAccepted(true);
        payload.put("accepted",true);assertFalse(RejectedCommandEvidence.matches(inbox,command));payload.remove("accepted");
        assertFalse(RejectedCommandEvidence.matches(inbox,command));payload.put("accepted",false);
        payload.put("replayed",true);assertFalse(RejectedCommandEvidence.matches(inbox,command));payload.remove("replayed");
        payload.put("errorCode","ROBOT_BUSY");assertFalse(RejectedCommandEvidence.matches(inbox,command));
        payload.put("errorCode","LOCALIZATION_NOT_READY");envelope.put("operationKey","foreign");
        assertFalse(RejectedCommandEvidence.matches(inbox,command));
    }
}
