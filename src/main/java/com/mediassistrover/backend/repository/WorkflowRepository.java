package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.Workflow;
import com.mediassistrover.backend.enums.WorkflowStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface WorkflowRepository extends JpaRepository<Workflow,UUID> {
    boolean existsByWorkflowCodeIgnoreCaseAndVersionNo(String code,int version);
    Optional<Workflow> findTopByWorkflowCodeIgnoreCaseOrderByVersionNoDesc(String code);
    Page<Workflow> findByStatus(WorkflowStatus status,Pageable pageable);
    List<Workflow> findByStatusAndAcceptsNewJobsTrueOrderByWorkflowCodeAscVersionNoDesc(WorkflowStatus status);
    Optional<Workflow> findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(String code,WorkflowStatus status);
    Optional<Workflow> findFirstByServiceTypeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(String serviceType,WorkflowStatus status);
    Optional<Workflow> findFirstByServiceTypeIgnoreCaseAndStatusAndAcceptsNewJobsTrueAndSchemaVersionOrderByVersionNoDesc(String serviceType,WorkflowStatus status,int schemaVersion);
    @EntityGraph(attributePaths={"startStep","publishedByAccount"}) Optional<Workflow> findDetailedByWorkflowId(UUID id);
}
