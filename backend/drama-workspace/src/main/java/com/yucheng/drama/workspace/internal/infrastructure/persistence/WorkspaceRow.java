package com.yucheng.drama.workspace.internal.infrastructure.persistence;

import java.time.Instant;

public record WorkspaceRow(String id, String name, String role, long revision,
                           Instant createdAt, Instant updatedAt) {}
