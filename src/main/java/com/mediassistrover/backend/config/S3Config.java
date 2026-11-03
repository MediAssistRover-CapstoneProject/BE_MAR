package com.mediassistrover.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {
    @Bean(destroyMethod = "close")
    S3Client s3Client(S3Properties properties) {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(c -> c.apiCallTimeout(java.time.Duration.ofSeconds(20))
                        .apiCallAttemptTimeout(java.time.Duration.ofSeconds(8)))
                .build();
    }
}
