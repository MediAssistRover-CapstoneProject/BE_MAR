package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mqtt.*;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Shared display and speech guidance. It never advances or completes a workflow step. */
@Service @RequiredArgsConstructor
public class NavigationGuidanceService {
    private final RobotNavigationSessionRepository sessions;
    private final RobotMqttSessionRepository links;
    private final JobRepository jobs;
    private final JobStepRepository steps;
    private final NavigationLivePoseService poses;
    private final MqttWire wire;
    private final Clock clock;
    @Value("${app.navigation.guidance.announce-distance-cm:35}") private double announceCm;
    @Value("${app.navigation.guidance.simplify-cm:2.5}") private double simplifyCm;
    @Value("${app.navigation.guidance.turn-angle-deg:35}") private double turnAngle;
    @Value("${app.navigation.guidance.max-path-offset-cm:25}") private double maxOffsetCm;
    @Value("${app.navigation.guidance.max-pose-age-ms:2000}") private long maxPoseAgeMs;

    private record Point(double x,double y,double distance) { }
    private record Projection(int segment,double fraction,double x,double y,double distance,double offset) { }

    @Transactional(readOnly=true)
    public ObjectNode view(UUID robot) {
        Instant now=Instant.now(clock);
        ObjectNode out=wire.object().put("state","IDLE").put("observedAt",now.toString());
        var session=sessions.findById(robot).orElse(null);
        if(session==null||!session.open(now)||session.getPlannedPathJson()==null)return out;
        JsonNode plan=session.getPlannedPathJson();
        if(!session.getSessionId().toString().equals(plan.path("sessionId").asText())
                ||session.getInitializationRevision()!=plan.path("initializationRevision").asLong(-1))return out;
        var job=jobs.findById(UUID.fromString(plan.path("jobId").asText())).orElse(null);
        var step=steps.findById(UUID.fromString(plan.path("jobStepId").asText())).orElse(null);
        if(job==null||step==null||job.getRobot()==null||!robot.equals(job.getRobot().getRobotId()))return out;
        out.set("plan",plan);
        if(step.getStatus()==JobStepStatus.SUCCEEDED){
            if(step.getEndedAt()==null||step.getEndedAt().isBefore(now.minusSeconds(20)))return out.put("state","IDLE");
            if(job.getSelectedJobStep()!=null&&"MOVE".equals(job.getSelectedJobStep().getStepTypeSnapshot())
                    &&!step.getJobStepId().equals(job.getSelectedJobStep().getJobStepId()))return out.put("state","IDLE");
            return out.put("state","ARRIVED").put("text","Đã đến "+plan.path("endpointName").asText("điểm đến"));
        }
        if(job.getStatus()!=JobStatus.RUNNING||job.getCancelRequestedAt()!=null
                ||step.getStatus()!=JobStepStatus.RUNNING||step.getInterruptionReason()!=null
                ||job.getSelectedJobStep()==null||!step.getJobStepId().equals(job.getSelectedJobStep().getJobStepId()))
            return out.put("state","STOPPED");
        out.put("state","WAITING_FOR_POSE").put("text","Đang chờ vị trí robot");
        var link=links.findById(robot).orElse(null);
        ObjectNode sample=poses.latest(robot);
        if(link==null||link.isClosed()||link.getLastHeartbeatAt()==null
                ||link.getLastHeartbeatAt().isBefore(now.minusSeconds(5))||sample==null
                ||!link.getTransportSessionId().toString().equals(sample.path("transportSessionId").asText())
                ||!link.getProducerSessionId().toString().equals(sample.path("producerSessionId").asText())
                ||!session.getSessionId().toString().equals(sample.path("navigationSessionId").asText())
                ||!session.getMap().getMapId().toString().equals(sample.path("mapId").asText())
                ||!session.getMapAssetHash().equalsIgnoreCase(sample.path("mapAssetHash").asText())
                ||session.getInitializationRevision()!=sample.path("initializationRevision").asLong(-1)
                ||Instant.parse(sample.path("capturedAt").asText()).isBefore(now.minusMillis(Math.max(500,maxPoseAgeMs))))return out;
        List<Point> points=new ArrayList<>();double length=0;
        for(JsonNode p:plan.path("points")){
            double x=p.path("xM").asDouble(),y=p.path("yM").asDouble();
            if(!points.isEmpty()){
                Point previous=points.getLast();double delta=Math.hypot(x-previous.x,y-previous.y);
                if(delta<0.00001)continue;
                length+=delta;
            }
            points.add(new Point(x,y,length));
        }
        if(points.size()<2)return out;
        ObjectNode displayPlan=((ObjectNode)plan).deepCopy();
        var displayPoints=displayPlan.putArray("points");
        for(Point point:points)displayPoints.addObject().put("xM",point.x).put("yM",point.y);
        out.set("plan",displayPlan);
        JsonNode pose=sample.path("pose");
        Projection position=project(points,pose.path("xM").asDouble(),pose.path("yM").asDouble(),pose.path("yawRad").asDouble());
        if(position.offset*100>Math.max(1,maxOffsetCm))return out.put("text","Đang cập nhật đường đi");
        out.put("state","TRACKING").put("capturedAt",sample.path("capturedAt").asText())
                .put("planRevision",plan.path("plannedAt").asText())
                .put("segmentIndex",position.segment).put("segmentFraction",position.fraction)
                .put("remainingCm",Math.round(Math.max(0,length-position.distance)*100));
        out.putObject("projection").put("xM",position.x).put("yM",position.y);
        List<Integer> corners=new ArrayList<>();corners.add(0);
        simplify(points,0,points.size()-1,Math.max(.005,simplifyCm/100),corners);
        corners.add(points.size()-1);
        Point straightStart=points.getFirst();
        Point segmentStart=points.get(position.segment),segmentEnd=points.get(position.segment+1);
        double heading=Math.atan2(segmentEnd.y-segmentStart.y,segmentEnd.x-segmentStart.x)-pose.path("yawRad").asDouble();
        boolean facingPath=Math.abs(Math.atan2(Math.sin(heading),Math.cos(heading)))<=Math.PI/4;
        for(int i=1;i<corners.size()-1;i++){
            Point a=points.get(corners.get(i-1)),b=points.get(corners.get(i)),c=points.get(corners.get(i+1));
            double angle=Math.atan2(c.y-b.y,c.x-b.x)-Math.atan2(b.y-a.y,b.x-a.x);
            angle=Math.atan2(Math.sin(angle),Math.cos(angle));
            if(Math.abs(Math.toDegrees(angle))<Math.max(15,turnAngle))continue;
            if(b.distance<=position.distance+.02){straightStart=b;continue;}
            String direction=Math.abs(angle)>Math.toRadians(150)?"UTURN":angle>0?"LEFT":"RIGHT";
            String instruction=switch(direction){case "LEFT"->"rẽ trái";case "RIGHT"->"rẽ phải";default->"quay lại";};
            long distance=Math.round((b.distance-position.distance)*100);
            String cue=step.getJobStepId()+":"+direction+":"+Math.round(b.x*20)+":"+Math.round(b.y*20);
            out.put("direction",direction).put("distanceCm",distance).put("cueId",cue)
                    .put("text","Còn "+distance+" cm, "+instruction);
            out.putObject("turn").put("xM",b.x).put("yM",b.y);
            if(distance<=Math.max(1,announceCm)){
                speech(out,step.getJobStepId(),cue,direction,b,"Phía trước "+instruction);
            }else if(facingPath)straight(out,step.getJobStepId(),straightStart);
            return out;
        }
        out.put("direction","STRAIGHT").put("distanceCm",out.path("remainingCm").asLong())
                .put("text","Đi thẳng "+out.path("remainingCm").asLong()+" cm tới điểm đến");
        if(facingPath&&out.path("remainingCm").asLong()>=25)straight(out,step.getJobStepId(),straightStart);
        return out;
    }

    private void straight(ObjectNode out,UUID step,Point anchor){
        String cue=step+":STRAIGHT:"+Math.round(anchor.x*5)+":"+Math.round(anchor.y*5);
        speech(out,step,cue,"STRAIGHT",anchor,"Mời bạn đi thẳng theo tôi.");
    }

    private void speech(ObjectNode out,UUID step,String cue,String direction,Point anchor,String text){
        out.put("cueId",cue);
        ObjectNode command=out.putObject("interaction").put("stepType","INTERACT")
                .put("parentJobStepId",step.toString()).put("cueId",cue).put("direction",direction);
        command.putObject("anchor").put("xM",anchor.x).put("yM",anchor.y);
        ObjectNode params=command.putObject("params").put("action","SPEAK").put("prompt_key","NAVIGATION_"+direction);
        params.putObject("data").put("text",text).put("language_code","vi").put("speed_wpm",150).put("volume",70);
    }

    private Projection project(List<Point> points,double x,double y,double yaw){
        Projection best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(int i=0;i<points.size()-1;i++){
            Point a=points.get(i),b=points.get(i+1);double dx=b.x-a.x,dy=b.y-a.y,len2=dx*dx+dy*dy;
            double t=Math.max(0,Math.min(1,((x-a.x)*dx+(y-a.y)*dy)/len2));
            double px=a.x+t*dx,py=a.y+t*dy,offset=Math.hypot(x-px,y-py);
            // Heading resolves nearby crossing segments without overriding spatial proximity.
            double score=offset+.02*(1-Math.cos(Math.atan2(dy,dx)-yaw));
            if(score<bestScore){bestScore=score;best=new Projection(i,t,px,py,a.distance+t*Math.sqrt(len2),offset);}
        }
        return best;
    }

    private void simplify(List<Point> points,int start,int end,double tolerance,List<Integer> result){
        if(end-start<2)return;
        Point a=points.get(start),b=points.get(end);double dx=b.x-a.x,dy=b.y-a.y,len2=dx*dx+dy*dy;
        double farthest=tolerance;int split=-1;
        for(int i=start+1;i<end;i++){
            Point p=points.get(i);double t=len2==0?0:Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy)/len2));
            double distance=Math.hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);
            if(distance>farthest){farthest=distance;split=i;}
        }
        if(split>=0){simplify(points,start,split,tolerance,result);result.add(split);simplify(points,split,end,tolerance,result);}
    }
}
