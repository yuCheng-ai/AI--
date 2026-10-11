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
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@Tag(name = "镜头")
public class ShotController {
    private final ContentOperations content;

    public ShotController(ContentOperations content) { this.content = content; }

    @GetMapping("/projects/{projectId}/shots")
    @Operation(summary = "按镜头编号分页查询")
    public ShotPage list(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
                         @AuthenticationPrincipal AuthenticatedUser actor,
                         @RequestParam(required = false) String cursor,
                         @RequestParam(defaultValue = "30") int limit) {
        return content.listShots(workspaceId, actor.userId(), projectId, cursor, limit);
    }

    @PostMapping("/projects/{projectId}/shots")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建镜头及第一份修订", description = "镜头编号由服务端按项目串行分配")
    public ShotView create(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
                           @AuthenticationPrincipal AuthenticatedUser actor,
                           @Valid @RequestBody CreateShot command) {
        return content.createShot(workspaceId, actor.userId(), projectId, command);
    }

    @GetMapping("/shots/{shotId}")
    @Operation(summary = "读取镜头当前修订")
    public ShotView get(@PathVariable UUID workspaceId, @PathVariable UUID shotId,
                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return content.getShot(workspaceId, actor.userId(), shotId);
    }

    @GetMapping("/shots/{shotId}/revisions/{revision}")
    @Operation(summary = "读取镜头指定历史修订")
    public ShotView getRevision(@PathVariable UUID workspaceId, @PathVariable UUID shotId,
                                @Parameter(schema = @Schema(type = "string", example = "1", pattern = "^[1-9][0-9]*$")) @PathVariable long revision, @AuthenticationPrincipal AuthenticatedUser actor) {
        return content.getShotRevision(workspaceId, actor.userId(), shotId, revision);
    }

    @PatchMapping("/shots/{shotId}")
    @Operation(summary = "追加镜头修订", description = "保存完整内容，旧修订保持不变；expectedRevision 不匹配返回 409")
    public ShotView revise(@PathVariable UUID workspaceId, @PathVariable UUID shotId,
                           @AuthenticationPrincipal AuthenticatedUser actor,
                           @Valid @RequestBody ReviseShot command) {
        return content.reviseShot(workspaceId, actor.userId(), shotId, command);
    }
}
