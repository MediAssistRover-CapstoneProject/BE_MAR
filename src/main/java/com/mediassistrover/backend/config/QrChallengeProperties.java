package com.mediassistrover.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.qr-challenge")
public record QrChallengeProperties(
        @NotBlank @Size(min = 32) String secret,
        @Min(30) @Max(300) int ttlSeconds
) { }
