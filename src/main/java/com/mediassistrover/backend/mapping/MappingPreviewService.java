package com.mediassistrover.backend.mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.repository.MappingSessionRepository;
import com.mediassistrover.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.util.zip.InflaterInputStream;

/** One bounded latest frame per open session; transient frames never become inbox/JobEvent rows. */
@Service @RequiredArgsConstructor
public class MappingPreviewService {
    private final MappingSessionRepository sessions;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final Map<UUID,Frame> latest=new ConcurrentHashMap<>();
    private record Frame(UUID sessionId,Instant captured,long revision,JsonNode data) { }
    public record Changed(UUID jobId,JsonNode preview) { }
    public void accept(UUID robotId,JsonNode payload) {
        UUID sessionId=UUID.fromString(payload.path("mapping_session_id").asText());var session=sessions.findById(sessionId).orElse(null);
        if(session==null || !session.getRobotId().equals(robotId) || !session.isPotentiallyOpen())return;
        Instant now=Instant.now(clock),captured=Instant.parse(payload.path("captured_at").asText());
        if(captured.isBefore(now.minusSeconds(10)) || captured.isAfter(now.plusSeconds(5)))return;
        long cells=(long)payload.path("width").asInt()*payload.path("height").asInt();
        if(!payload.path("width").canConvertToInt() || !payload.path("height").canConvertToInt() || cells<1 || cells>4_000_000)throw new BusinessRuleException("Preview grid exceeds 4 million cells");
        try(var inflated=new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(payload.path("data").asText())))) {
            byte[] raster=inflated.readNBytes((int)cells+1);if(raster.length!=cells)throw new BusinessRuleException("Preview raster length differs from grid");
            for(byte value:raster)if(value< -1 || value>100)throw new BusinessRuleException("Invalid occupancy preview value");
        } catch(IOException | IllegalArgumentException ex){throw new BusinessRuleException("Invalid zlib/base64 preview");}
        latest.entrySet().removeIf(e->e.getValue().captured().isBefore(now.minusSeconds(10)));
        Frame frame=new Frame(sessionId,captured,payload.path("revision").asLong(),payload.deepCopy());
        Frame selected=latest.compute(robotId,(id,prior)->prior!=null && prior.sessionId().equals(sessionId)
                && (!captured.isAfter(prior.captured()) || frame.revision()<=prior.revision())?prior:frame);
        if(selected==frame)events.publishEvent(new Changed(session.getJobId(),frame.data().deepCopy()));
    }
    public JsonNode latest(UUID sessionId) {
        var session=sessions.findById(sessionId).orElse(null);if(session==null)return null;Frame frame=latest.get(session.getRobotId());
        if(frame==null || !frame.sessionId().equals(sessionId))return null;
        if(!session.isPotentiallyOpen() || frame.captured().isBefore(Instant.now(clock).minusSeconds(10))) {latest.remove(session.getRobotId(),frame);return null;}
        return frame.data().deepCopy();
    }
}
