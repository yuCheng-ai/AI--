package com.yucheng.drama.workspace.internal.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Global identity and membership SQL; every operation has explicit identity predicates. */
@Mapper
public interface WorkspaceMapper {
    @InterceptorIgnore(tenantLine = "true")
    UserRow findUserByUsername(@Param("username") String username);
    @InterceptorIgnore(tenantLine = "true")
    UserRow findUserById(@Param("userId") UUID userId);
    @InterceptorIgnore(tenantLine = "true")
    int insertUser(@Param("id") UUID id, @Param("username") String username,
                   @Param("passwordHash") String passwordHash);
    @InterceptorIgnore(tenantLine = "true")
    List<WorkspaceRow> listForActor(@Param("actorId") UUID actorId,
                                   @Param("cursorTime") Instant cursorTime,
                                   @Param("cursorId") UUID cursorId, @Param("limit") int limit);
    @InterceptorIgnore(tenantLine = "true")
    WorkspaceRow findForActor(@Param("workspaceId") UUID workspaceId, @Param("actorId") UUID actorId);
    @InterceptorIgnore(tenantLine = "true")
    String findRole(@Param("workspaceId") UUID workspaceId, @Param("actorId") UUID actorId);
    @InterceptorIgnore(tenantLine = "true")
    int insertWorkspace(@Param("id") UUID id, @Param("name") String name, @Param("actorId") UUID actorId);
    @InterceptorIgnore(tenantLine = "true")
    int insertAdminMembership(@Param("workspaceId") UUID workspaceId, @Param("actorId") UUID actorId);
    @InterceptorIgnore(tenantLine = "true")
    int renameWorkspace(@Param("workspaceId") UUID workspaceId, @Param("name") String name,
                        @Param("expectedRevision") long expectedRevision);
    @InterceptorIgnore(tenantLine = "true")
    int archiveWorkspace(@Param("workspaceId") UUID workspaceId, @Param("expectedRevision") long expectedRevision);
}
