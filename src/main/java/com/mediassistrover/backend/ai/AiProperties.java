package com.mediassistrover.backend.ai;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.util.UUID;

@Component @ConfigurationProperties("app.ai") @Getter @Setter @Validated
public class AiProperties {
    private boolean sensingEnabled;
    private UUID restrictedAlertRoomId;
    @Min(1) @Max(3600) private int restrictedAlertCooldownSeconds = 60;
}
