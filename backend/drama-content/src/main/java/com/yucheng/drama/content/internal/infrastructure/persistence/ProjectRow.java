package com.yucheng.drama.content.internal.infrastructure.persistence;

import java.time.Instant;

public record ProjectRow(String id, String workspaceId, String name, String description,
                         String aspectRatio, long revision, Instant createdAt, Instant updatedAt) {}
