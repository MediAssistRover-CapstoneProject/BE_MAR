package com.mediassistrover.backend.media;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.media")
@Getter @Setter
public class MediaProperties {
    private boolean enabled;
    private String url = "";
    private String internalUrl = "http://127.0.0.1:7880";
    private String apiKey = "";
    private String apiSecret = "";
    private int tokenTtlSec = 60;
    private int sessionMaxSec = 1800;
    private int ringTimeoutSec = 60;
    private int deviceTimeoutSec = 35;
}
