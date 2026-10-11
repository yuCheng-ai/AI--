package com.yucheng.drama.workspace.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkspaceDtos {
    private WorkspaceDtos() {}

    public record CreateWorkspace(@NotBlank @Size(max = 120) String name) {}
    public record UpdateWorkspace(@NotBlank @Size(max = 120) String name,
                                  @Schema(type = "string", example = "1", pattern = "^[1-9][0-9]*$", description = "原样回传 revision 字符串，不在客户端转换成浮点数")
                                  @NotNull @Positive Long expectedRevision) {}
    public record WorkspaceView(UUID id, String name, WorkspaceRole role,
                                @Schema(type = "string", example = "1") String revision,
                                Instant createdAt, Instant updatedAt) {}
    public record WorkspacePage(List<WorkspaceView> items, String nextCursor) {}
    public record UserView(UUID id, String username) {}
    public record LoginResponse(UserView user,
                                @Schema(description = "登录后必须重新 GET /api/v1/auth/csrf 获取令牌")
                                boolean csrfRefreshRequired) {}
    public record CsrfResponse(String headerName, String parameterName, String token) {}
}
