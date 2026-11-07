package com.mediassistrover.backend.speech;

import com.mediassistrover.backend.entity.SystemSetting;
import com.mediassistrover.backend.enums.SystemSettingDataType;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.repository.AccountRepository;
import com.mediassistrover.backend.repository.SystemSettingRepository;
import com.mediassistrover.backend.service.AuditService;
import com.mediassistrover.backend.service.RequestMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared speech configuration. Cache hits never begin a DB transaction. */
@Service
public class SpeechSettingsService {
    public static final String KEY="robot.speech.profile";
    public static final Set<String> VOICES=Set.of("alloy","ash","ballad","coral","echo","fable","nova",
            "onyx","sage","shimmer","verse","marin","cedar");
    public record Profile(String voice,double speedMultiplier,String instructions,String revision) { }
    private record Cached(Profile profile,long until) { }
    private final SystemSettingRepository settings;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final Clock clock;
    private final Profile defaults;
    private volatile Cached cached;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(SpeechSettingsService.class);

    public SpeechSettingsService(SystemSettingRepository settings,AccountRepository accounts,AuditService audit,Clock clock,
            @Value("${app.tts.voice:coral}") String voice,
            @Value("${app.tts.speed:1.25}") double speed,
            @Value("${app.tts.instructions:Speak Vietnamese with a clear Northern Vietnamese accent and a calm adult female tone. Read only the supplied text.}") String instructions) {
        this.settings=settings;this.accounts=accounts;this.audit=audit;this.clock=clock;
        this.defaults=profile(voice,speed,instructions);
    }

    public Profile current() {
        Cached value=cached;
        if(value!=null&&System.nanoTime()<value.until)return value.profile;
        return reload();
    }

    private synchronized Profile reload() {
        Cached value=cached;
        if(value!=null&&System.nanoTime()<value.until)return value.profile;
        Profile result=value==null?defaults:value.profile;
        try {
            var row=settings.findBySettingKeyAndActiveTrue(KEY).orElse(null);
            if(row==null)result=defaults;
            else if(row.getSettingValue().get("value") instanceof Map<?,?> data
                    &&data.get("voice") instanceof String voice
                    &&data.get("speedMultiplier") instanceof Number speed
                    &&data.get("instructions") instanceof String instructions)
                result=profile(voice,speed.doubleValue(),instructions);
        } catch(org.springframework.dao.DataAccessException|BusinessRuleException error) {
            log.warn("SPEECH_SETTINGS_REFRESH_UNAVAILABLE: keeping last validated speech profile");
        }
        cached=new Cached(result,System.nanoTime()+5_000_000_000L);
        return result;
    }

    @Transactional
    public Profile update(Double speed,String voice,String instructions,UUID actorId,RequestMetadata metadata) {
        if(speed==null&&voice==null&&instructions==null)throw new BusinessRuleException("Provide a speech speed, voice or instructions");
        Profile before=current();
        Profile next=profile(voice==null?before.voice:voice.trim(),speed==null?before.speedMultiplier:speed,
                instructions==null?before.instructions:instructions.trim());
        var actor=accounts.findById(actorId).orElseThrow(()->new ResourceNotFoundException("Account not found"));
        var row=settings.findBySettingKey(KEY).orElseGet(SystemSetting::new);
        row.setSettingKey(KEY);row.setSettingValue(Map.of("value",Map.of("voice",next.voice,
                "speedMultiplier",next.speedMultiplier,"instructions",next.instructions)));
        row.setDataType(SystemSettingDataType.OBJECT);row.setCategory("SPEECH");
        row.setDescription("Robot speech profile; applied to subsequent synthesized phrases");
        row.setPublicSetting(false);row.setActive(true);row.setUpdatedBy(actor);row.setUpdatedAt(Instant.now(clock));
        settings.saveAndFlush(row);
        audit.record(actor,"SPEECH_SETTINGS_UPDATED","SystemSetting",row.getSystemSettingId(),
                Map.of("voice",before.voice,"speedMultiplier",before.speedMultiplier),
                Map.of("voice",next.voice,"speedMultiplier",next.speedMultiplier),metadata);
        Runnable publish=()->cached=new Cached(next,System.nanoTime()+5_000_000_000L);
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){publish.run();}
            });
        else publish.run();
        return next;
    }

    static Profile profile(String voice,double speed,String instructions) {
        if(!VOICES.contains(voice)||!Double.isFinite(speed)||speed<.25||speed>4.
                ||instructions==null||instructions.length()>1000)
            throw new BusinessRuleException("Invalid speech settings: supported voice and speed 0.25..4.0 required");
        String revision=OpenAiTtsService.sha256((voice+"\n"+speed+"\n"+instructions).getBytes(StandardCharsets.UTF_8)).substring(0,16);
        return new Profile(voice,speed,instructions,revision);
    }
}
