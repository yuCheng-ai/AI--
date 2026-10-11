package com.yucheng.drama.workspace.internal.web;

import com.yucheng.drama.workspace.api.AuthenticatedUser;
import com.yucheng.drama.workspace.api.WorkspaceDtos.*;
import com.yucheng.drama.workspace.internal.application.WorkspaceService;
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
@RequestMapping("/api/v1/workspaces")
@Tag(name = "工作空间")
public class WorkspaceController {
    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "分页列出当前用户的工作空间")
    public WorkspacePage list(@AuthenticationPrincipal AuthenticatedUser actor,
                              @RequestParam(required = false) String cursor,
                              @RequestParam(defaultValue = "30") int limit) {
        return service.list(actor.userId(), cursor, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建工作空间并成为管理员")
    public WorkspaceView create(@AuthenticationPrincipal AuthenticatedUser actor,
                                @Valid @RequestBody CreateWorkspace command) {
        return service.create(actor.userId(), command);
    }

    @GetMapping("/{workspaceId}")
    @Operation(summary = "读取工作空间")
    public WorkspaceView get(@PathVariable UUID workspaceId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.get(workspaceId, actor.userId());
    }

    @PatchMapping("/{workspaceId}")
    @Operation(summary = "修改工作空间名称", description = "仅管理员；expectedRevision 不匹配返回 409")
    public WorkspaceView update(@PathVariable UUID workspaceId, @AuthenticationPrincipal AuthenticatedUser actor,
                                @Valid @RequestBody UpdateWorkspace command) {
        return service.update(workspaceId, actor.userId(), command);
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "归档工作空间", description = "仅管理员；保留业务记录，不执行物理删除")
    public void archive(@PathVariable UUID workspaceId, @AuthenticationPrincipal AuthenticatedUser actor,
                        @Parameter(schema = @Schema(type = "string", example = "1", pattern = "^[1-9][0-9]*$")) @RequestParam long expectedRevision) {
        service.archive(workspaceId, actor.userId(), expectedRevision);
    }
}
