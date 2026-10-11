package com.yucheng.drama.workspace.api;

import java.util.UUID;

/** Authorization is checked against current database membership on every call. */
public interface WorkspaceAccess {
    void requireMember(UUID workspaceId, UUID actorId);
    void requireEditor(UUID workspaceId, UUID actorId);
    void requireAdmin(UUID workspaceId, UUID actorId);
}
