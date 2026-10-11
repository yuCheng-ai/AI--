package com.yucheng.drama.content.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ContentDtos {
    private ContentDtos() {}

    public record CreateProject(@NotBlank @Size(max = 120) String name,
                                @Size(max = 4000) String description,
                                @Pattern(regexp = "9:16|16:9|1:1") String aspectRatio) {}
    public record UpdateProject(@NotBlank @Size(max = 120) String name,
                                @Size(max = 4000) String description,
                                @NotBlank @Pattern(regexp = "9:16|16:9|1:1") String aspectRatio,
                                @Schema(type = "string", example = "1", pattern = "^[1-9][0-9]*$", description = "原样回传 revision 字符串，不在客户端转换成浮点数")
                                  @NotNull @Positive Long expectedRevision) {}
    public record ProjectView(UUID id, UUID workspaceId, String name, String description, String aspectRatio,
                              @Schema(type = "string", example = "1") String revision,
                              Instant createdAt, Instant updatedAt) {}
    public record ProjectPage(List<ProjectView> items, String nextCursor) {}

    public record CreateShot(@NotBlank @Size(max = 120) String title,
                             @Size(max = 12000) String description,
                             @Size(max = 8000) String dialogue,
                             @NotNull @Min(1) @Max(60) Integer durationSeconds) {}
    public record ReviseShot(@NotBlank @Size(max = 120) String title,
                             @Size(max = 12000) String description,
                             @Size(max = 8000) String dialogue,
                             @NotNull @Min(1) @Max(60) Integer durationSeconds,
                             @Schema(type = "string", example = "1", pattern = "^[1-9][0-9]*$", description = "原样回传 revision 字符串，不在客户端转换成浮点数")
                                  @NotNull @Positive Long expectedRevision) {}
    public record ShotView(UUID id, UUID workspaceId, UUID projectId, int shotNumber,
                           String title, String description, String dialogue, int durationSeconds,
                           @Schema(type = "string", example = "1") String revision,
                           Instant createdAt, Instant updatedAt) {}
    public record ShotPage(List<ShotView> items, String nextCursor) {}
}
