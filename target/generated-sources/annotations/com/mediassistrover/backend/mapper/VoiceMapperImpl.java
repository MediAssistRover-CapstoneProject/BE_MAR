package com.mediassistrover.backend.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.dto.voice.response.VoiceResponses;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.Robot;
import com.mediassistrover.backend.entity.ServiceRequest;
import com.mediassistrover.backend.entity.VoiceCommand;
import com.mediassistrover.backend.entity.VoiceInteraction;
import com.mediassistrover.backend.enums.VoiceCommandStatus;
import com.mediassistrover.backend.enums.VoiceInteractionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-13T11:40:03+0700",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class VoiceMapperImpl implements VoiceMapper {

    @Override
    public VoiceResponses.InteractionResponse toResponse(VoiceInteraction entity) {
        if ( entity == null ) {
            return null;
        }

        UUID accountId = null;
        UUID robotId = null;
        UUID voiceInteractionId = null;
        String languageCode = null;
        VoiceInteractionStatus status = null;
        Instant startedAt = null;
        Instant endedAt = null;
        String detectedIntent = null;
        String transcriptSummary = null;
        String audioUri = null;

        accountId = entityAccountAccountId( entity );
        robotId = entityRobotRobotId( entity );
        voiceInteractionId = entity.getVoiceInteractionId();
        languageCode = entity.getLanguageCode();
        status = entity.getStatus();
        startedAt = entity.getStartedAt();
        endedAt = entity.getEndedAt();
        detectedIntent = entity.getDetectedIntent();
        transcriptSummary = entity.getTranscriptSummary();
        audioUri = entity.getAudioUri();

        VoiceResponses.InteractionResponse interactionResponse = new VoiceResponses.InteractionResponse( voiceInteractionId, accountId, robotId, languageCode, status, startedAt, endedAt, detectedIntent, transcriptSummary, audioUri );

        return interactionResponse;
    }

    @Override
    public VoiceResponses.CommandResponse toResponse(VoiceCommand entity) {
        if ( entity == null ) {
            return null;
        }

        UUID voiceInteractionId = null;
        UUID serviceRequestId = null;
        JsonNode parameters = null;
        UUID voiceCommandId = null;
        int sequenceNo = 0;
        String rawText = null;
        String normalizedText = null;
        String intentCode = null;
        BigDecimal confidenceScore = null;
        String commandType = null;
        VoiceCommandStatus status = null;
        Instant receivedAt = null;
        Instant processedAt = null;

        voiceInteractionId = entityVoiceInteractionVoiceInteractionId( entity );
        serviceRequestId = entityServiceRequestServiceRequestId( entity );
        parameters = entity.getParametersJson();
        voiceCommandId = entity.getVoiceCommandId();
        sequenceNo = entity.getSequenceNo();
        rawText = entity.getRawText();
        normalizedText = entity.getNormalizedText();
        intentCode = entity.getIntentCode();
        confidenceScore = entity.getConfidenceScore();
        commandType = entity.getCommandType();
        status = entity.getStatus();
        receivedAt = entity.getReceivedAt();
        processedAt = entity.getProcessedAt();

        VoiceResponses.CommandResponse commandResponse = new VoiceResponses.CommandResponse( voiceCommandId, voiceInteractionId, serviceRequestId, sequenceNo, rawText, normalizedText, intentCode, confidenceScore, commandType, parameters, status, receivedAt, processedAt );

        return commandResponse;
    }

    private UUID entityAccountAccountId(VoiceInteraction voiceInteraction) {
        Account account = voiceInteraction.getAccount();
        if ( account == null ) {
            return null;
        }
        return account.getAccountId();
    }

    private UUID entityRobotRobotId(VoiceInteraction voiceInteraction) {
        Robot robot = voiceInteraction.getRobot();
        if ( robot == null ) {
            return null;
        }
        return robot.getRobotId();
    }

    private UUID entityVoiceInteractionVoiceInteractionId(VoiceCommand voiceCommand) {
        VoiceInteraction voiceInteraction = voiceCommand.getVoiceInteraction();
        if ( voiceInteraction == null ) {
            return null;
        }
        return voiceInteraction.getVoiceInteractionId();
    }

    private UUID entityServiceRequestServiceRequestId(VoiceCommand voiceCommand) {
        ServiceRequest serviceRequest = voiceCommand.getServiceRequest();
        if ( serviceRequest == null ) {
            return null;
        }
        return serviceRequest.getServiceRequestId();
    }
}
