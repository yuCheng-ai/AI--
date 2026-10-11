package com.yucheng.drama.content.internal.application;

import com.yucheng.drama.content.api.ContentDtos.*;
import com.yucheng.drama.content.api.ContentOperations;
import com.yucheng.drama.content.internal.infrastructure.persistence.ContentMapper;
import com.yucheng.drama.content.internal.infrastructure.persistence.ProjectRow;
import com.yucheng.drama.content.internal.infrastructure.persistence.ShotRow;
import com.yucheng.drama.kernel.api.BusinessException;
import com.yucheng.drama.kernel.api.WorkspaceScope;
import com.yucheng.drama.workspace.api.WorkspaceAccess;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentService implements ContentOperations {
    private final ContentMapper mapper;
    private final WorkspaceAccess access;

    public ContentService(ContentMapper mapper, WorkspaceAccess access) {
        this.mapper = mapper;
        this.access = access;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPage listProjects(UUID workspaceId, UUID actorId, String cursor, int limit) {
        access.requireMember(workspaceId, actorId);
        validateLimit(limit);
        ProjectCursor after = ProjectCursor.parse(cursor);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            List<ProjectRow> rows = mapper.listProjects(workspaceId, after.time(), after.id(), limit + 1);
            boolean more = rows.size() > limit;
            List<ProjectRow> page = rows.subList(0, Math.min(rows.size(), limit));
            return new ProjectPage(page.stream().map(ContentService::view).toList(),
                    more ? ProjectCursor.encode(page.getLast()) : null);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectView getProject(UUID workspaceId, UUID actorId, UUID projectId) {
        access.requireMember(workspaceId, actorId);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            return view(requiredProject(workspaceId, projectId));
        }
    }

    @Override
    @Transactional
    public ProjectView createProject(UUID workspaceId, UUID actorId, CreateProject command) {
        access.requireEditor(workspaceId, actorId);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            UUID id = UUID.randomUUID();
            mapper.insertProject(workspaceId, id, command.name().strip(), empty(command.description()),
                    command.aspectRatio() == null ? "9:16" : command.aspectRatio(), actorId);
            return view(requiredProject(workspaceId, id));
        }
    }

    @Override
    @Transactional
    public ProjectView updateProject(UUID workspaceId, UUID actorId, UUID projectId, UpdateProject command) {
        access.requireEditor(workspaceId, actorId);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            requiredProject(workspaceId, projectId);
            if (mapper.updateProject(workspaceId, projectId, command.name().strip(), empty(command.description()),
                    command.aspectRatio(), command.expectedRevision(), actorId) != 1) {
                throw revisionConflict();
            }
            return view(requiredProject(workspaceId, projectId));
        }
    }

    @Override
    @Transactional
    public void archiveProject(UUID workspaceId, UUID actorId, UUID projectId, long expectedRevision) {
        access.requireAdmin(workspaceId, actorId);
        validateRevision(expectedRevision);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            requiredProject(workspaceId, projectId);
            if (mapper.archiveProject(workspaceId, projectId, expectedRevision, actorId) != 1) {
                throw revisionConflict();
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ShotPage listShots(UUID workspaceId, UUID actorId, UUID projectId, String cursor, int limit) {
        access.requireMember(workspaceId, actorId);
        validateLimit(limit);
        int afterNumber = parseShotCursor(cursor);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            requiredProject(workspaceId, projectId);
            List<ShotRow> rows = mapper.listShots(workspaceId, projectId, afterNumber, limit + 1);
            boolean more = rows.size() > limit;
            List<ShotRow> page = rows.subList(0, Math.min(rows.size(), limit));
            return new ShotPage(page.stream().map(ContentService::view).toList(),
                    more ? Integer.toString(page.getLast().shotNumber()) : null);
        }
    }

    @Override
    @Transactional
    public ShotView createShot(UUID workspaceId, UUID actorId, UUID projectId, CreateShot command) {
        access.requireEditor(workspaceId, actorId);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            // The parent row serializes numbering and prevents concurrent project archival.
            lockProject(workspaceId, projectId);
            int number = mapper.nextShotNumber(workspaceId, projectId);
            UUID id = UUID.randomUUID();
            mapper.insertShot(workspaceId, id, projectId, number, actorId);
            mapper.insertShotRevision(workspaceId, id, 1, command.title().strip(), empty(command.description()),
                    empty(command.dialogue()), command.durationSeconds(), actorId);
            return view(requiredShot(workspaceId, id));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ShotView getShot(UUID workspaceId, UUID actorId, UUID shotId) {
        access.requireMember(workspaceId, actorId);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            ShotRow shot = requiredShot(workspaceId, shotId);
            requiredProject(workspaceId, UUID.fromString(shot.projectId()));
            return view(shot);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ShotView getShotRevision(UUID workspaceId, UUID actorId, UUID shotId, long revision) {
        access.requireMember(workspaceId, actorId);
        validateRevision(revision);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            ShotRow current = requiredShot(workspaceId, shotId);
            requiredProject(workspaceId, UUID.fromString(current.projectId()));
            ShotRow historical = mapper.findShotRevision(workspaceId, shotId, revision);
            if (historical == null) throw BusinessException.notFound("SHOT_REVISION_NOT_FOUND", "镜头修订不存在");
            return view(historical);
        }
    }

    @Override
    @Transactional
    public ShotView reviseShot(UUID workspaceId, UUID actorId, UUID shotId, ReviseShot command) {
        access.requireEditor(workspaceId, actorId);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            ShotRow current = requiredShot(workspaceId, shotId);
            lockProject(workspaceId, UUID.fromString(current.projectId()));
            if (mapper.advanceShotRevision(workspaceId, shotId, command.expectedRevision(), actorId) != 1) {
                throw revisionConflict();
            }
            mapper.insertShotRevision(workspaceId, shotId, command.expectedRevision() + 1,
                    command.title().strip(), empty(command.description()), empty(command.dialogue()),
                    command.durationSeconds(), actorId);
            return view(requiredShot(workspaceId, shotId));
        }
    }

    private ProjectRow requiredProject(UUID workspaceId, UUID projectId) {
        ProjectRow row = mapper.findProject(workspaceId, projectId);
        if (row == null) throw BusinessException.notFound("PROJECT_NOT_FOUND", "项目不存在或已归档");
        return row;
    }

    private void lockProject(UUID workspaceId, UUID projectId) {
        if (mapper.lockProject(workspaceId, projectId) == null) {
            throw BusinessException.notFound("PROJECT_NOT_FOUND", "项目不存在或已归档");
        }
    }

    private ShotRow requiredShot(UUID workspaceId, UUID shotId) {
        ShotRow row = mapper.findShot(workspaceId, shotId);
        if (row == null) throw BusinessException.notFound("SHOT_NOT_FOUND", "镜头不存在");
        return row;
    }

    private static ProjectView view(ProjectRow row) {
        return new ProjectView(UUID.fromString(row.id()), UUID.fromString(row.workspaceId()), row.name(),
                row.description(), row.aspectRatio(), Long.toString(row.revision()), row.createdAt(), row.updatedAt());
    }

    private static ShotView view(ShotRow row) {
        return new ShotView(UUID.fromString(row.id()), UUID.fromString(row.workspaceId()), UUID.fromString(row.projectId()),
                row.shotNumber(), row.title(), row.description(), row.dialogue(), row.durationSeconds(),
                Long.toString(row.revision()), row.createdAt(), row.updatedAt());
    }

    private static String empty(String value) { return value == null ? "" : value; }
    private static BusinessException revisionConflict() {
        return BusinessException.conflict("REVISION_CONFLICT", "内容已被修改，请读取当前版本后重试");
    }
    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) throw BusinessException.badRequest("INVALID_PAGE_SIZE", "每页数量须在 1 到 100 之间");
    }
    private static void validateRevision(long revision) {
        if (revision < 1) throw BusinessException.badRequest("INVALID_REVISION", "修订号必须为正数");
    }
    private static int parseShotCursor(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            int result = Integer.parseInt(value);
            if (result < 1) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("INVALID_CURSOR", "分页游标无效");
        }
    }
    private record ProjectCursor(Instant time, UUID id) {
        static ProjectCursor parse(String value) {
            if (value == null || value.isBlank()) return new ProjectCursor(null, null);
            if (value.length() > 256) throw BusinessException.badRequest("INVALID_CURSOR", "分页游标无效");
            try {
                String[] fields = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
                if (fields.length != 2) throw new IllegalArgumentException();
                return new ProjectCursor(Instant.parse(fields[0]), UUID.fromString(fields[1]));
            } catch (RuntimeException ex) {
                throw BusinessException.badRequest("INVALID_CURSOR", "分页游标无效");
            }
        }
        static String encode(ProjectRow row) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (row.createdAt() + "|" + row.id()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
