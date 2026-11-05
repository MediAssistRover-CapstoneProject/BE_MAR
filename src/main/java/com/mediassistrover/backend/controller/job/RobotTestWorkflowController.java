package com.mediassistrover.backend.controller.job;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.GraphCreateRequest;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.RobotTestWorkflowService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
@Tag(name="Robot test workflows",description="Commissioning using existing MOVE, WAIT, sensing, speech and CONTROL_HARDWARE steps")
public class RobotTestWorkflowController {
    private final RobotTestWorkflowService tests;
    private final com.mediassistrover.backend.speech.OpenAiSttService stt;
    @GetMapping("/api/v1/test-workflows/by-idempotency-key")
    @Operation(summary="Find a previous test job without issuing another command")
    public org.springframework.http.ResponseEntity<RobotTestWorkflowService.Result> lookup(@RequestParam String key,@AuthenticationPrincipal AccountPrincipal actor){
        var result=tests.lookup(key,actor);
        return result==null?org.springframework.http.ResponseEntity.noContent().build():org.springframework.http.ResponseEntity.ok(result);
    }
    @PostMapping("/api/v1/test-workflows/preview")
    @Operation(summary="Preview a basic-step workflow; no robot command or database write")
    public GraphCreateRequest preview(@Valid @RequestBody RobotTestWorkflowService.Create body){return tests.preview(body);}
    @PostMapping("/api/v1/test-workflows/run")
    @Operation(summary="Create/reuse a test workflow and run one job",description="ADMIN only. Reuse Idempotency-Key for retries of the exact same request. MOVE requires the current READY map session. Servo opening requires commissioning unlock enabled on BE and Pi.")
    public RobotTestWorkflowService.Result run(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RobotTestWorkflowService.Create body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){
        return tests.run(body,key,actor,RequestMetadataFactory.from(request));
    }
    @PostMapping("/api/v1/robots/{robotId}/audio-loopback-test")
    @Operation(summary="Record the robot microphone and replay the original WAV on its speaker",
            description="ADMIN only, Idempotency-Key required. Returns a job ID. After a short beep, Pi records PCM16 mono 16 kHz for 1..15 seconds, then replays that recording. No STT/TTS request, text conversion, model or map required. Temporary audio is deleted when the step ends or is cancelled.")
    public RobotTestWorkflowService.Result loopback(@PathVariable UUID robotId,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RobotTestWorkflowService.AudioLoopback body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){
        var step=new RobotTestWorkflowService.TestStep(RobotTestWorkflowService.Kind.AUDIO_LOOPBACK,null,
                body.durationSec()==null?5.:body.durationSec().doubleValue(),null,null,body.volume());
        return tests.run(new RobotTestWorkflowService.Create(robotId,null,null,null,null,List.of(step)),key,actor,RequestMetadataFactory.from(request));
    }
    @PostMapping("/api/v1/robots/{robotId}/voice-test")
    @Operation(summary="Command the robot microphone to record, transcribe and optionally speak back",
            description="ADMIN only. Returns a job ID immediately. Pi records PCM16 mono 16 kHz WAV, uploads it over HTTPS for STT and returns text in the VOICE_CAPTURE result. Optional playback is an ordinary INTERACT/SPEAK step. Audio is temporary, no RAG, motion or localization reset. Reuse the same Idempotency-Key only for retries of this request.")
    public RobotTestWorkflowService.Result voice(@PathVariable UUID robotId,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RobotTestWorkflowService.Voice body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){
        if(!stt.configuration().enabled()||!stt.configuration().configured())
            throw new com.mediassistrover.backend.exception.BusinessRuleException("STT_NOT_CONFIGURED: enable STT on BE before commanding the microphone");
        var steps=new ArrayList<RobotTestWorkflowService.TestStep>();
        String language=body.languageCode()==null?"vi":body.languageCode();
        steps.add(new RobotTestWorkflowService.TestStep(RobotTestWorkflowService.Kind.VOICE_CAPTURE,null,
                body.durationSec()==null?5.:body.durationSec().doubleValue(),null,language,null));
        if(Boolean.TRUE.equals(body.playback()))steps.add(new RobotTestWorkflowService.TestStep(
                RobotTestWorkflowService.Kind.SPEAK_LAST_TRANSCRIPT,null,null,null,language,body.volume()));
        return tests.run(new RobotTestWorkflowService.Create(robotId,null,null,null,null,steps),key,actor,RequestMetadataFactory.from(request));
    }
    @GetMapping("/api/v1/robots/{robotId}/test-configuration")
    public ObjectNode configuration(@PathVariable UUID robotId){return tests.configuration(robotId);}
    @PostMapping("/api/v1/robots/{robotId}/servo-test")
    @Operation(summary="Open/close servo through a single CONTROL_HARDWARE job",description="Returns a job ID; a published command does not prove mechanical door position. Does not reset navigation or localization.")
    public RobotTestWorkflowService.Result servo(@PathVariable UUID robotId,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RobotTestWorkflowService.Servo body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){
        var kind="OPEN".equals(body.action())?RobotTestWorkflowService.Kind.OPEN_SERVO:RobotTestWorkflowService.Kind.CLOSE_SERVO;
        return tests.run(new RobotTestWorkflowService.Create(robotId,null,null,null,body.compartmentId(),
                List.of(new RobotTestWorkflowService.TestStep(kind,null,null))),key,actor,RequestMetadataFactory.from(request));
    }
}
