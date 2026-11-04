package com.mediassistrover.backend.dto.media.response;

import java.util.List;

public record MediaListResponse(List<MediaObjectResponse> content, String nextCursor, boolean hasMore) { }
