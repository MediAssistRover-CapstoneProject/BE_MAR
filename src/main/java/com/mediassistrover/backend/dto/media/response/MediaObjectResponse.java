package com.mediassistrover.backend.dto.media.response;

public record MediaObjectResponse(String objectKey, String url, String mediaType, String contentType,
                                  long sizeBytes, String sha256) { }
