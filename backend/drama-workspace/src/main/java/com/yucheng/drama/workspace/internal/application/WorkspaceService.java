package com.yucheng.drama.workspace.internal.application;

import com.yucheng.drama.kernel.api.BusinessException;
import com.yucheng.drama.kernel.api.WorkspaceScope;
import com.yucheng.drama.workspace.api.WorkspaceAccess;
import com.yucheng.drama.workspace.api.WorkspaceDtos.*;
import com.yucheng.drama.workspace.api.WorkspaceRole;
import com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper;
import com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceRow;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService implements WorkspaceAccess {
    private final WorkspaceMapper mapper;

    public WorkspaceService(WorkspaceMapper mapper) { this.mapper = mapper; }

    @Override
    public void requireMember(UUID workspaceId, UUID actorId) { role(workspaceId, actorId); }

    @Override
    public void requireEditor(UUID workspaceId, UUID actorId) {
        if (!role(workspaceId, actorId).canEdit()) {
            throw BusinessException.forbidden("WORKSPACE_EDIT_FORBIDDEN", "当前成员没有编辑权限");
        }
    }

    @Override
    public void requireAdmin(UUID workspaceId, UUID actorId) {
        if (role(workspaceId, actorId) != WorkspaceRole.ADMIN) {
            throw BusinessException.forbidden("WORKSPACE_ADMIN_REQUIRED", "需要工作空间管理员权限");
        }
    }

    @Transactional(readOnly = true)
    public WorkspacePage list(UUID actorId, String cursor, int limit) {
        requireActiveUser(actorId);
        if (limit < 1 || limit > 100) throw BusinessException.badRequest("INVALID_PAGE_SIZE", "每页数量须在 1 到 100 之间");
        Cursor after = Cursor.parse(cursor);
        List<WorkspaceRow> rows = mapper.listForActor(actorId, after.time(), after.id(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<WorkspaceRow> page = rows.subList(0, Math.min(rows.size(), limit));
        String next = hasMore ? Cursor.encode(page.getLast()) : null;
        return new WorkspacePage(page.stream().map(WorkspaceService::view).toList(), next);
    }

    @Transactional(readOnly = true)
    public WorkspaceView get(UUID workspaceId, UUID actorId) {
        requireMember(workspaceId, actorId);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            return view(required(workspaceId, actorId));
        }
    }

    @Transactional
    public WorkspaceView create(UUID actorId, CreateWorkspace command) {
        requireActiveUser(actorId);
        UUID id = UUID.randomUUID();
        try (var ignored = WorkspaceScope.open(id)) {
            mapper.insertWorkspace(id, command.name().strip(), actorId);
            mapper.insertAdminMembership(id, actorId);
            return view(required(id, actorId));
        }
    }

    @Transactional
    public WorkspaceView update(UUID workspaceId, UUID actorId, UpdateWorkspace command) {
        requireAdmin(workspaceId, actorId);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            if (mapper.renameWorkspace(workspaceId, command.name().strip(), command.expectedRevision()) != 1) {
                throw BusinessException.conflict("REVISION_CONFLICT", "工作空间已更新，请刷新后重试");
            }
            return view(required(workspaceId, actorId));
        }
    }

    @Transactional
    public void archive(UUID workspaceId, UUID actorId, long expectedRevision) {
        requireAdmin(workspaceId, actorId);
        if (expectedRevision < 1) throw BusinessException.badRequest("INVALID_REVISION", "修订号必须为正数");
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            if (mapper.archiveWorkspace(workspaceId, expectedRevision) != 1) {
                throw BusinessException.conflict("REVISION_CONFLICT", "工作空间已更新，请刷新后重试");
            }
        }
    }

    private WorkspaceRole role(UUID workspaceId, UUID actorId) {
        if (workspaceId == null || actorId == null) {
            throw BusinessException.forbidden("WORKSPACE_ACCESS_DENIED", "无法访问此工作空间");
        }
        String value = mapper.findRole(workspaceId, actorId);
        if (value == null) throw BusinessException.forbidden("WORKSPACE_ACCESS_DENIED", "无法访问此工作空间");
        return WorkspaceRole.valueOf(value);
    }

    private void requireActiveUser(UUID actorId) {
        var user = mapper.findUserById(actorId);
        if (user == null || !user.enabled()) {
            throw BusinessException.forbidden("USER_DISABLED", "用户已停用，请重新登录");
        }
    }

    private WorkspaceRow required(UUID id, UUID actorId) {
        WorkspaceRow row = mapper.findForActor(id, actorId);
        if (row == null) throw BusinessException.notFound("WORKSPACE_NOT_FOUND", "工作空间不存在或已归档");
        return row;
    }

    private static WorkspaceView view(WorkspaceRow row) {
        return new WorkspaceView(UUID.fromString(row.id()), row.name(), WorkspaceRole.valueOf(row.role()),
                Long.toString(row.revision()), row.createdAt(), row.updatedAt());
    }

    private record Cursor(Instant time, UUID id) {
        static Cursor parse(String value) {
            if (value == null || value.isBlank()) return new Cursor(null, null);
            if (value.length() > 256) throw BusinessException.badRequest("INVALID_CURSOR", "分页游标无效");
            try {
                String[] fields = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
                if (fields.length != 2) throw new IllegalArgumentException();
                return new Cursor(Instant.parse(fields[0]), UUID.fromString(fields[1]));
            } catch (RuntimeException ex) {
                throw BusinessException.badRequest("INVALID_CURSOR", "分页游标无效");
            }
        }
        static String encode(WorkspaceRow row) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (row.createdAt() + "|" + row.id()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
