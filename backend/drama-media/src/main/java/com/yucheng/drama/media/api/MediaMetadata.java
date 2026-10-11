package com.yucheng.drama.media.api;

import java.time.Instant;
import java.util.UUID;

/** The id identifies an immutable uploaded media version, not a physical path. */
public record MediaMetadata(
        UUID id,
        UUID workspaceId,
        int version,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        MediaStatus status,
        Instant createdAt) {
}
