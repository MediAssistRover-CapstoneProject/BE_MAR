package com.mediassistrover.backend.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.*;
import java.time.Instant;
public interface JobExecutionService {
    Job createForRequest(ServiceRequest request);
    /** System battery policy only; invokes the same schema 3 step/outbox path as other jobs. */
    Job createForBatteryReturn(java.util.UUID workflowId,Robot robot,FacilityMap map,JsonNode inputs,java.util.UUID episodeId);
    Job createForBatteryWarning(java.util.UUID workflowId,Robot robot,JsonNode inputs,java.util.UUID episodeId);
    /** Returns null when a patrol occurrence is skipped before any Job is created. */
    Job createForSchedule(Schedule schedule,Instant fireAt,JsonNode input);
    Job createForSpeech(java.util.UUID workflowId,java.util.UUID robotId,String text,String languageCode);
    Job createForMapping(java.util.UUID workflowId,java.util.UUID robotId,java.util.UUID floorId,String mapName);
    Job createForTest(java.util.UUID workflowId,java.util.UUID robotId,java.util.UUID mapId,java.util.UUID navigationSessionId,JsonNode input);
    Job createForPublished(java.util.UUID workflowId,java.util.UUID robotId,java.util.UUID mapId,
                           java.util.UUID navigationSessionId,JsonNode inputs,Integer priority);
}
