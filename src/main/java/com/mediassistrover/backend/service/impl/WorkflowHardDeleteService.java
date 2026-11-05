package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.workflow.response.WorkflowResponses.WorkflowHardDeleteResponse;
import com.mediassistrover.backend.enums.WorkflowStatus;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

/** Ordered physical deletion for a workflow. The caller owns the transaction and rollout lock. */
@Service
@RequiredArgsConstructor
public class WorkflowHardDeleteService {
    private static final String JOB_JOIN =
            " JOIN JobTask jt ON jt.job_task_id=js.job_task_id JOIN Job j ON j.job_id=jt.job_id WHERE j.workflow_id=?";

    private final JdbcTemplate jdbc;

    @Transactional(propagation=Propagation.MANDATORY)
    public WorkflowHardDeleteResponse delete(UUID workflowId) {
        byte[] id=bytes(workflowId);
        List<WorkflowSnapshot> workflows=jdbc.query(
                "SELECT workflow_code,status FROM Workflow WHERE workflow_id=? FOR UPDATE",
                (rs,row)->new WorkflowSnapshot(rs.getString("workflow_code"),WorkflowStatus.valueOf(rs.getString("status"))),id);
        if(workflows.isEmpty())throw new ResourceNotFoundException("Workflow not found: "+workflowId);
        WorkflowSnapshot workflow=workflows.getFirst();

        List<String> active=jdbc.query(
                "SELECT status FROM Job WHERE workflow_id=? FOR UPDATE",
                (rs,row)->rs.getString(1),id).stream()
                .filter(status->status.equals("QUEUED")||status.equals("ACCEPTED")||status.equals("RUNNING"))
                .toList();
        if(!active.isEmpty())throw new ConflictException(
                "Workflow has active Jobs (QUEUED, ACCEPTED or RUNNING); cancel or finish them before force delete");

        int jobs=count("SELECT COUNT(*) FROM Job WHERE workflow_id=?",id);
        int schedules=count("SELECT COUNT(*) FROM Schedule WHERE workflow_id=?",id);
        int navigation=count("SELECT COUNT(*) FROM NavigationRequest WHERE workflow_id=?",id);
        int runtime=0;

        // Leaves source ServiceRequest/NavigationRequest records intact as requested.
        jdbc.update("UPDATE NavigationRequest SET workflow_id=NULL WHERE workflow_id=?",id);

        runtime+=delete("DELETE r FROM RobotTestSubmission r JOIN Job j ON j.job_id=r.job_id WHERE j.workflow_id=?",id);
        runtime+=delete("DELETE a FROM MapArtifact a JOIN MappingSession s ON s.session_id=a.session_id JOIN Job j ON j.job_id=s.job_id WHERE j.workflow_id=?",id);
        runtime+=delete("DELETE s FROM MappingSession s JOIN Job j ON j.job_id=s.job_id WHERE j.workflow_id=?",id);
        runtime+=delete("DELETE ar FROM AlertRecipient ar JOIN Alert a ON a.alert_id=ar.alert_id JOIN JobStep js ON js.job_step_id=a.job_step_id"+JOB_JOIN,id);
        runtime+=delete("DELETE a FROM Alert a JOIN JobStep js ON js.job_step_id=a.job_step_id"+JOB_JOIN,id);
        runtime+=delete("DELETE o FROM RobotOperationAuthorization o JOIN JobStep js ON js.job_step_id=o.job_step_id"+JOB_JOIN,id);
        runtime+=delete("DELETE n FROM JobNavigationSelection n JOIN JobStep js ON js.job_step_id=n.selector_job_step_id"+JOB_JOIN,id);
        runtime+=delete("DELETE a FROM AiObservation a JOIN JobStep js ON js.job_step_id=a.job_step_id"+JOB_JOIN,id);
        runtime+=delete("DELETE c FROM CrowdDensityRecord c JOIN JobStep js ON js.job_step_id=c.job_step_id"+JOB_JOIN,id);
        runtime+=delete("DELETE r FROM MqttOperationRecoveryReceipt r JOIN JobStep js ON js.job_step_id=r.job_step_id"+JOB_JOIN,id);
        runtime+=delete("DELETE r FROM JobStepResponse r JOIN JobStep js ON js.job_step_id=r.job_step_id"+JOB_JOIN,id);
        runtime+=delete("DELETE c FROM RobotCommandOutbox c JOIN Job j ON j.job_id=c.job_id WHERE j.workflow_id=?",id);
        runtime+=delete("DELETE q FROM QrChallenge q JOIN Job j ON j.job_id=q.job_id WHERE j.workflow_id=?",id);
        runtime+=delete("DELETE a FROM AuthVerification a JOIN Job j ON j.job_id=a.job_id WHERE j.workflow_id=?",id);
        runtime+=delete("DELETE e FROM JobEvent e JOIN Job j ON j.job_id=e.job_id WHERE j.workflow_id=?",id);
        runtime+=delete("DELETE w FROM WorkflowOrchestrationWork w JOIN Job j ON j.job_id=w.job_id WHERE j.workflow_id=?",id);
        runtime+=delete("DELETE h FROM RobotStatusHistory h JOIN Job j ON j.job_id=h.job_id WHERE j.workflow_id=?",id);
        runtime+=delete("DELETE h FROM RobotLocationHistory h JOIN Job j ON j.job_id=h.job_id WHERE j.workflow_id=?",id);

        jdbc.update("UPDATE Job SET selected_job_step_id=NULL WHERE workflow_id=?",id);
        runtime+=delete("DELETE js FROM JobStep js"+JOB_JOIN,id);
        runtime+=delete("DELETE jt FROM JobTask jt JOIN Job j ON j.job_id=jt.job_id WHERE j.workflow_id=?",id);

        // Break both internal and external recovery chains before deleting the selected Jobs.
        jdbc.update("UPDATE Job SET recovery_of_job_id=NULL WHERE workflow_id=?",id);
        jdbc.update("UPDATE Job dependent JOIN Job target ON target.job_id=dependent.recovery_of_job_id " +
                "SET dependent.recovery_of_job_id=NULL WHERE target.workflow_id=?",id);
        jdbc.update("DELETE FROM Job WHERE workflow_id=?",id);
        jdbc.update("DELETE FROM Schedule WHERE workflow_id=?",id);

        jdbc.update("UPDATE Workflow SET start_step_id=NULL WHERE workflow_id=?",id);
        jdbc.update("DELETE FROM StepTransition WHERE workflow_id=?",id);
        jdbc.update("DELETE FROM Step WHERE workflow_id=?",id);
        jdbc.update("DELETE FROM Task WHERE workflow_id=?",id);
        jdbc.update("DELETE FROM Workflow WHERE workflow_id=?",id);

        return new WorkflowHardDeleteResponse(workflowId,workflow.code(),workflow.status(),jobs,schedules,navigation,runtime);
    }

    private int delete(String sql,byte[] id){return jdbc.update(sql,id);}
    private int count(String sql,byte[] id){Integer value=jdbc.queryForObject(sql,Integer.class,id);return value==null?0:value;}
    private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    private record WorkflowSnapshot(String code,WorkflowStatus status) {}
}
