package com.mediassistrover.backend.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.aws.s3")
@Validated
@Getter
@Setter
public class S3Properties {
    private boolean enabled = true;
    @NotBlank
    private String region;
    @NotBlank
    private String bucket;
}
