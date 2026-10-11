package com.yucheng.drama.content.internal.infrastructure.persistence;

import java.time.Instant;

public record ShotRow(String id, String workspaceId, String projectId, int shotNumber,
                      String title, String description, String dialogue, int durationSeconds,
                      long revision, Instant createdAt, Instant updatedAt) {}
