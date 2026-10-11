package com.yucheng.drama.content.internal.web;

import com.yucheng.drama.content.api.ContentDtos.*;
import com.yucheng.drama.content.api.ContentOperations;
import com.yucheng.drama.workspace.api.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")
@Tag(name = "项目")
public class ProjectController {
    private final ContentOperations content;

    public ProjectController(ContentOperations content) { this.content = content; }

    @GetMapping
    @Operation(summary = "分页查询项目")
    public ProjectPage list(@PathVariable UUID workspaceId, @AuthenticationPrincipal AuthenticatedUser actor,
                            @RequestParam(required = false) String cursor,
                            @RequestParam(defaultValue = "30") int limit) {
        return content.listProjects(workspaceId, actor.userId(), cursor, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建项目", description = "需要编辑者或管理员权限，默认画幅为 9:16")
    public ProjectView create(@PathVariable UUID workspaceId, @AuthenticationPrincipal AuthenticatedUser actor,
                              @Valid @RequestBody CreateProject command) {
        return content.createProject(workspaceId, actor.userId(), command);
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "读取项目")
    public ProjectView get(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return content.getProject(workspaceId, actor.userId(), projectId);
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "修改项目", description = "提交完整可编辑字段；expectedRevision 不匹配返回 409")
    public ProjectView update(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
                              @AuthenticationPrincipal AuthenticatedUser actor,
                              @Valid @RequestBody UpdateProject command) {
        return content.updateProject(workspaceId, actor.userId(), projectId, command);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "归档项目", description = "仅管理员；镜头和历史修订仍保留")
    public void archive(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
                        @AuthenticationPrincipal AuthenticatedUser actor, @Parameter(schema = @Schema(type = "string", example = "1", pattern = "^[1-9][0-9]*$")) @RequestParam long expectedRevision) {
        content.archiveProject(workspaceId, actor.userId(), projectId, expectedRevision);
    }
}
