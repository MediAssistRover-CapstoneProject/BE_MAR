package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.GraphCreateRequest;
import com.mediassistrover.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Builds trusted sequential MOVE graphs; endpoint identities remain request data. */
@Component @RequiredArgsConstructor
public class NavigationRouteWorkflowFactory {
    private final ObjectMapper json;
    public GraphCreateRequest build(int count,double speed) {
        if(count<1||count>20||!Double.isFinite(speed)||speed<.05||speed>.6)
            throw new BusinessRuleException("Route requires 1..20 endpoints and speed 0.05..0.60 m/s");
        try(var in=new ClassPathResource("static/navigation-move-workflow.json").getInputStream()) {
            ObjectNode root=(ObjectNode)json.readTree(in),w=(ObjectNode)root.path("workflow"),g=(ObjectNode)root.path("graph");
            w.put("workflowCode","WF_NAV_ROUTE_"+UUID.randomUUID().toString().replace("-","").toUpperCase())
                .put("versionNo",1).put("workflowName","Route through "+count+" endpoints")
                .put("description","Ordered MOVE steps in one navigation session; advance only after confirmed arrival")
                .put("maxTotalVisits",count).put("maxDurationSec",count*180+60);
            ObjectNode schema=(ObjectNode)w.path("inputSchemaJson");
            ((ObjectNode)schema.path("properties")).putObject("route_waypoint_ids")
                .put("type","array").put("minItems",count-1).put("maxItems",count-1)
                .putObject("items").put("type","string").put("format","uuid");
            ((ArrayNode)schema.path("required")).add("route_waypoint_ids");
            ArrayNode outputs=json.createArrayNode();
            w.set("outputMappingJson",json.createObjectNode().set("stops",outputs));
            ObjectNode prototype=((ObjectNode)g.path("steps").get(0)).deepCopy();
            ArrayNode steps=json.createArrayNode(),edges=json.createArrayNode();
            for(int i=0;i<count;i++) {
                String code="MOVE_"+String.format("%02d",i+1);
                ObjectNode s=prototype.deepCopy();s.put("stepCode",code).put("stepName","Visit stop "+(i+1)).put("sequenceNo",i+1);
                ((ObjectNode)s.path("paramsJson")).put("max_speed_mps",speed);
                ((ObjectNode)s.path("inputMappingJson")).set("endpoint_id",json.createObjectNode().put("$ref",
                    i==count-1?"/inputs/endpoint_id":"/inputs/route_waypoint_ids/"+i));
                steps.add(s);outputs.addObject().put("$ref","/outputs/"+code);
                ObjectNode success=edge(code,"SUCCESS");
                if(i==count-1)success.put("terminalStatus","SUCCEEDED");else success.put("toStepCode","MOVE_"+String.format("%02d",i+2));
                edges.add(success);edges.add(edge(code,"FAILURE").put("terminalStatus","FAILED"));
                edges.add(edge(code,"TIMEOUT").put("terminalStatus","RECOVERY_REQUIRED"));
            }
            g.put("startStepCode","MOVE_01").set("steps",steps);g.set("transitions",edges);
            return json.treeToValue(root,GraphCreateRequest.class);
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot build navigation route definition",e);}
    }
    private ObjectNode edge(String code,String outcome){return json.createObjectNode().put("fromStepCode",code).put("outcome",outcome).put("priority",1).put("defaultTransition",true);}
}
