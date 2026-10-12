package com.mediassistrover.backend.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.*;
import java.time.Instant;
public interface JobExecutionService {
    Job createForRequest(ServiceRequest request);
    Job createForSchedule(Schedule schedule,Instant fireAt,JsonNode input);
    Job createForMapping(java.util.UUID workflowId,java.util.UUID robotId,java.util.UUID floorId,String mapName);
}
