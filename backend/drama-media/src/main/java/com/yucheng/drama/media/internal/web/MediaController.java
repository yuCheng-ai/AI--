package com.yucheng.drama.media.internal.web;

import com.yucheng.drama.kernel.api.BusinessException;
import com.yucheng.drama.media.api.MediaMetadata;
import com.yucheng.drama.media.internal.application.MediaInput;
import com.yucheng.drama.media.internal.application.MediaService;
import com.yucheng.drama.workspace.api.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@ConditionalOnProperty(name = "drama.runtime.role", havingValue = "api", matchIfMissing = true)
@RequestMapping("/api/v1/workspaces/{workspaceId}/media")
@Tag(name = "媒体", description = "本地磁盘媒体上传与受控读取；STORED 表示已存储，尚未转码或完成媒体探测。")
public final class MediaController {
    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "上传媒体", description = "本地模式通过业务 API 接收 multipart 文件并流式写入磁盘，不是对象存储签名直传。需要编辑权限和 CSRF Token。")
    public ResponseEntity<MediaMetadata> upload(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID workspaceId, @RequestPart("file") MultipartFile file) {
        var metadata = service.upload(actorId(principal), workspaceId,
                new MediaInput(file.getOriginalFilename(), file.getContentType(), file.getSize(), file::getInputStream));
        return ResponseEntity.created(URI.create("/api/v1/workspaces/" + workspaceId + "/media/" + metadata.id()))
                .body(metadata);
    }

    @GetMapping
    @Operation(summary = "最近上传的媒体", description = "默认返回最近 50 条；limit 范围为 1 至 100。")
    public List<MediaMetadata> recent(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID workspaceId, @RequestParam(defaultValue = "50") int limit) {
        return service.recent(actorId(principal), workspaceId, limit);
    }

    @GetMapping("/{mediaId}")
    @Operation(summary = "读取媒体元数据")
    public MediaMetadata get(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID workspaceId, @PathVariable UUID mediaId) {
        return service.get(actorId(principal), workspaceId, mediaId);
    }

    @GetMapping("/{mediaId}/content")
    @Operation(summary = "读取媒体内容", description = "校验当前成员权限后返回可重复读取的 Resource，由 Spring MVC 处理 Range 请求；download=true 返回附件。")
    public ResponseEntity<Resource> content(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID workspaceId, @PathVariable UUID mediaId,
            @RequestParam(defaultValue = "false") boolean download) {
        var result = service.download(actorId(principal), workspaceId, mediaId);
        var metadata = result.metadata();
        var disposition = download ? ContentDisposition.attachment() : ContentDisposition.inline();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition.filename(metadata.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new StoredMediaResource(result));
    }

    private static UUID actorId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(401, "AUTHENTICATION_REQUIRED", "请先登录。");
        }
        return principal.userId();
    }
}
