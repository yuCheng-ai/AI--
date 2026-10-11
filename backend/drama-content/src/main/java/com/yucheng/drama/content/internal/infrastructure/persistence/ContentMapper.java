package com.yucheng.drama.content.internal.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContentMapper {
    List<ProjectRow> listProjects(@Param("workspaceId") UUID workspaceId,
                                  @Param("cursorTime") Instant cursorTime,
                                  @Param("cursorId") UUID cursorId, @Param("limit") int limit);
    ProjectRow findProject(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId);
    ProjectRow lockProject(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId);
    int insertProject(@Param("workspaceId") UUID workspaceId, @Param("id") UUID id,
                      @Param("name") String name, @Param("description") String description,
                      @Param("aspectRatio") String aspectRatio, @Param("actorId") UUID actorId);
    int updateProject(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId,
                      @Param("name") String name, @Param("description") String description,
                      @Param("aspectRatio") String aspectRatio, @Param("expectedRevision") long expectedRevision,
                      @Param("actorId") UUID actorId);
    int archiveProject(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId,
                       @Param("expectedRevision") long expectedRevision, @Param("actorId") UUID actorId);
    List<ShotRow> listShots(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId,
                           @Param("afterNumber") int afterNumber, @Param("limit") int limit);
    ShotRow findShot(@Param("workspaceId") UUID workspaceId, @Param("shotId") UUID shotId);
    ShotRow findShotRevision(@Param("workspaceId") UUID workspaceId, @Param("shotId") UUID shotId,
                            @Param("revision") long revision);
    int nextShotNumber(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId);
    int insertShot(@Param("workspaceId") UUID workspaceId, @Param("id") UUID id,
                   @Param("projectId") UUID projectId, @Param("shotNumber") int shotNumber,
                   @Param("actorId") UUID actorId);
    int insertShotRevision(@Param("workspaceId") UUID workspaceId, @Param("shotId") UUID shotId,
                           @Param("revision") long revision, @Param("title") String title,
                           @Param("description") String description, @Param("dialogue") String dialogue,
                           @Param("durationSeconds") int durationSeconds, @Param("actorId") UUID actorId);
    int advanceShotRevision(@Param("workspaceId") UUID workspaceId, @Param("shotId") UUID shotId,
                            @Param("expectedRevision") long expectedRevision, @Param("actorId") UUID actorId);
}
