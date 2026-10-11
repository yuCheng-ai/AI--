package com.yucheng.drama.content.api;

import com.yucheng.drama.content.api.ContentDtos.*;
import java.util.UUID;

/** Content boundary; persistence models never leave this module. */
public interface ContentOperations {
    ProjectPage listProjects(UUID workspaceId, UUID actorId, String cursor, int limit);
    ProjectView getProject(UUID workspaceId, UUID actorId, UUID projectId);
    ProjectView createProject(UUID workspaceId, UUID actorId, CreateProject command);
    ProjectView updateProject(UUID workspaceId, UUID actorId, UUID projectId, UpdateProject command);
    void archiveProject(UUID workspaceId, UUID actorId, UUID projectId, long expectedRevision);
    ShotPage listShots(UUID workspaceId, UUID actorId, UUID projectId, String cursor, int limit);
    ShotView createShot(UUID workspaceId, UUID actorId, UUID projectId, CreateShot command);
    ShotView getShot(UUID workspaceId, UUID actorId, UUID shotId);
    ShotView getShotRevision(UUID workspaceId, UUID actorId, UUID shotId, long revision);
    ShotView reviseShot(UUID workspaceId, UUID actorId, UUID shotId, ReviseShot command);
}
