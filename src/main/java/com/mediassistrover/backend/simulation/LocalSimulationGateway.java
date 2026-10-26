package com.mediassistrover.backend.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.mqtt.PahoMqttTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/** File-only simulator gateway. No repositories, real robot commands, or map DB. */
@Service
public class LocalSimulationGateway {
    private final ObjectMapper json;
    private final ObjectProvider<PahoMqttTransport> transport;
    private final boolean enabled;
    private final String robotId;
    private final Path mapFile;
    private ObjectNode telemetry;
    private Instant receivedAt = Instant.EPOCH;
    private long lastSequence = -1;
    private String sessionId = "", ownedRun = "", owner = "";
    private Instant commandAt = Instant.EPOCH;
    private ObjectNode lastAck;
    private String publishError = "";

    public LocalSimulationGateway(ObjectMapper json, ObjectProvider<PahoMqttTransport> transport,
            @Value("${app.simulation.enabled:false}") boolean enabled,
            @Value("${app.simulation.robot-id:9cd72e40-5778-4ee8-b5c3-e1030089e3a1}") String robotId,
            @Value("${app.simulation.map-file:/app/local-simulation/map.json}") String mapFile) {
        this.json=json; this.transport=transport; this.enabled=enabled;
        this.robotId=UUID.fromString(robotId).toString(); this.mapFile=Path.of(mapFile);
    }
    public boolean enabled() { return enabled; }
    public String prefix() { return "mar/robots/"+robotId+"/simulation/"; }
    public boolean accepts(String topic) { return enabled && (topic.equals(prefix()+"telemetry") || topic.equals(prefix()+"acks")); }
    private JsonNode model() {
        try {
            JsonNode m=json.readTree(Files.readString(mapFile));
            if (!"LOCAL_MOCK".equals(m.path("sourceType").asText()) || !"mar-map-v2".equals(m.path("schema").asText())) throw new IllegalArgumentException();
            return m;
        } catch (Exception e) { throw fail(HttpStatus.SERVICE_UNAVAILABLE,"Chưa nạp được map LiDAR từ file local."); }
    }
    private static ResponseStatusException fail(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
    private boolean fresh() { return receivedAt.isAfter(Instant.now().minusSeconds(5)) && transport.getObject().connected(); }
    public synchronized void receive(String topic,byte[] bytes,int qos,boolean retained) {
        if (!accepts(topic) || retained || bytes.length>262144) return;
        try {
            JsonNode raw=json.readTree(bytes);
            if (!"MAR-SIM-1".equals(raw.path("protocol").asText()) || !robotId.equals(raw.path("robotId").asText())) return;
            Instant sent=Instant.parse(raw.path("sentAt").asText());
            if (sent.isBefore(Instant.now().minusSeconds(5)) || sent.isAfter(Instant.now().plusSeconds(2))) return;
            if (topic.endsWith("/acks")) {
                if(qos==1 && sessionId.equals(raw.path("sessionId").asText())) lastAck=(ObjectNode)raw;
                return;
            }
            if(qos!=0 || !raw.isObject()) return;
            String session=UUID.fromString(raw.path("sessionId").asText()).toString();
            long sequence=raw.path("sequence").asLong(-1);
            if(session.equals(sessionId) && sequence<=lastSequence) return;
            if(!session.equals(sessionId)) {ownedRun="";owner="";lastAck=null;}
            sessionId=session;lastSequence=sequence;receivedAt=Instant.now();telemetry=(ObjectNode)raw;
        } catch(Exception ignored) { /* Invalid data never changes readiness. */ }
    }
    public synchronized ObjectNode status(String user,String runId) {
        ObjectNode out=telemetry==null?json.createObjectNode():telemetry.deepCopy();
        boolean live=enabled && fresh();
        out.put("enabled",enabled).put("mqttConnected",transport.getObject().connected()).put("connected",live);
        out.put("receivedAt",receivedAt.toString());out.put("publishError",publishError);
        if(!live) {out.put("ready",false);out.put("poseFresh",false);out.putNull("pose");out.put("reason","Chưa có telemetry mới từ Gazebo qua MQTT.");}
        if(lastAck!=null) out.set("lastAck",lastAck);
        if(live && owner.equals(user) && !ownedRun.isBlank() && ownedRun.equals(runId)) {
            ObjectNode lease=json.createObjectNode().put("runId",runId);
            publish("LEASE",lease);
        }
        return out;
    }
    public synchronized ObjectNode command(String user,ObjectNode body) {
        if(!enabled) throw fail(HttpStatus.SERVICE_UNAVAILABLE,"Chế độ mô phỏng chưa bật trên BE.");
        if(!fresh()) throw fail(HttpStatus.SERVICE_UNAVAILABLE,"Chưa kết nối bộ mô phỏng qua MQTT.");
        String kind=body.path("commandType").asText();
        if(!java.util.Set.of("START_PATROL","NAVIGATE_TO","CANCEL_PATROL","GET_ROBOT_STATUS").contains(kind)) throw fail(HttpStatus.BAD_REQUEST,"Lệnh mô phỏng không hợp lệ.");
        if(kind.equals("GET_ROBOT_STATUS")) return status(user,"");
        if(kind.equals("CANCEL_PATROL")) {
            if(!owner.equals(user) || !ownedRun.equals(body.path("runId").asText())) throw fail(HttpStatus.CONFLICT,"Không có hành trình thuộc phiên này.");
            return publish(kind,body);
        }
        String status=telemetry.path("status").asText();
        if(java.util.Set.of("STARTING","RUNNING","CANCELING").contains(status) || commandAt.isAfter(Instant.now().minusSeconds(5))) throw fail(HttpStatus.CONFLICT,"Đang có hành trình hoặc lệnh chờ xử lý.");
        if(!telemetry.path("ready").asBoolean()) throw fail(HttpStatus.CONFLICT,"Nav2 chưa sẵn sàng.");
        validate(body,model());
        if(!body.path("mapKey").asText().equals(telemetry.path("mapKey").asText())) throw fail(HttpStatus.CONFLICT,"Map FE/BE/Gazebo chưa đồng bộ.");
        owner=user;ownedRun=UUID.randomUUID().toString();body.put("runId",ownedRun);commandAt=Instant.now();
        return publish(kind,body);
    }
    private void validate(ObjectNode body,JsonNode m) {
        if(!m.path("mapId").asText().equals(body.path("mapId").asText()) || !m.path("mapVersion").asText().equals(body.path("mapKey").asText())) throw fail(HttpStatus.BAD_REQUEST,"Sai phiên bản map local.");
        JsonNode steps=body.path("steps");
        if(!steps.isArray() || steps.isEmpty() || steps.size()>100) throw fail(HttpStatus.BAD_REQUEST,"Cần 1–100 bước MOVE.");
        for(JsonNode p:steps) {
            for(String field:java.util.List.of("x","y","yaw","speed")) if(!p.path(field).isNumber() || !Double.isFinite(p.path(field).asDouble())) throw fail(HttpStatus.BAD_REQUEST,"Tọa độ không hợp lệ.");
            double speed=p.path("speed").asDouble();
            if(speed<.01 || speed>.26 || p.path("endpointId").asText().isBlank()) throw fail(HttpStatus.BAD_REQUEST,"Điểm đích hoặc tốc độ không hợp lệ (0,01–0,26 m/s).");
            double yaw=m.path("origin").get(2).asDouble(), r=m.path("resolution").asDouble();
            double a=p.path("x").asDouble()-m.path("origin").get(0).asDouble(), b=p.path("y").asDouble()-m.path("origin").get(1).asDouble();
            int w=m.path("width").asInt(),h=m.path("height").asInt();
            double px=(Math.cos(yaw)*a+Math.sin(yaw)*b)/r, py=h-(-Math.sin(yaw)*a+Math.cos(yaw)*b)/r, radius=.3/r;
            for(int y=(int)Math.floor(py-radius);y<=Math.floor(py+radius);y++) for(int x=(int)Math.floor(px-radius);x<=Math.floor(px+radius);x++) {
                double dx=Math.max(Math.max(x-px,0),px-x-1),dy=Math.max(Math.max(y-py,0),py-y-1);
                if(Math.hypot(dx,dy)<=radius && (x<0||y<0||x>=w||y>=h||m.path("occupancyGrid").get(y*w+x).asInt()!=0)) throw fail(HttpStatus.BAD_REQUEST,"Điểm quá gần tường hoặc vùng chưa biết.");
            }
        }
    }
    private ObjectNode publish(String kind,ObjectNode body) {
        ObjectNode message=body.deepCopy();String id=UUID.randomUUID().toString();
        message.put("protocol","MAR-SIM-1").put("robotId",robotId).put("sessionId",sessionId).put("commandType",kind)
            .put("correlationId",id).put("requestedAt",Instant.now().toString()).put("expiresAt",Instant.now().plusSeconds(3).toString());
        try {
            transport.getObject().publish(prefix()+"commands",json.writeValueAsBytes(message),1).whenComplete((ok,error)->{synchronized(this){publishError=error==null?"":"Không gửi được lệnh qua MQTT.";}});
        } catch(Exception e) {throw fail(HttpStatus.SERVICE_UNAVAILABLE,"Không gửi được lệnh qua MQTT.");}
        return json.createObjectNode().put("accepted",true).put("correlationId",id).put("runId",body.path("runId").asText());
    }
}
