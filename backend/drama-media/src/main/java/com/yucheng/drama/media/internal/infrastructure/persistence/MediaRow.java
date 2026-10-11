package com.yucheng.drama.media.internal.infrastructure.persistence;

import java.time.Instant;

public record MediaRow(
        String mediaId,
        String workspaceId,
        String objectId,
        String backendId,
        String bucket,
        String objectKey,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        String status,
        int versionNumber,
        Instant createdAt) {
}
