package com.yucheng.drama.media.internal.application;

import com.yucheng.drama.kernel.api.BusinessException;
import com.yucheng.drama.kernel.api.WorkspaceScope;
import com.yucheng.drama.media.api.MediaMetadata;
import com.yucheng.drama.media.api.MediaQueries;
import com.yucheng.drama.media.api.MediaStatus;
import com.yucheng.drama.media.internal.infrastructure.persistence.MediaMapper;
import com.yucheng.drama.media.internal.infrastructure.persistence.MediaRow;
import com.yucheng.drama.media.internal.application.port.ObjectStorage;
import com.yucheng.drama.media.internal.application.port.StorageLimitExceededException;
import com.yucheng.drama.media.internal.config.MediaStorageProperties;
import com.yucheng.drama.workspace.api.WorkspaceAccess;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("!migrate")
public final class MediaService implements MediaQueries {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final String UPLOAD_BUCKET = "uploads";
    private final WorkspaceAccess workspaceAccess;
    private final MediaMapper mapper;
    private final ObjectStorage storage;
    private final TransactionTemplate transactions;
    private final long maximumBytes;
    private final Clock clock;

    public MediaService(WorkspaceAccess workspaceAccess, MediaMapper mapper, ObjectStorage storage,
            PlatformTransactionManager transactionManager, Clock clock,
            MediaStorageProperties properties) {
        this.workspaceAccess = workspaceAccess;
        this.mapper = mapper;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maximumBytes = properties.maxFileSize().toBytes();
        this.clock = clock;
    }

    public MediaMetadata upload(UUID actorId, UUID workspaceId, MediaInput file) {
        workspaceAccess.requireEditor(workspaceId, actorId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Streaming uploads must not run inside a database transaction.");
        }
        if (file.sizeBytes() <= 0) {
            throw BusinessException.badRequest("MEDIA_EMPTY", "不能上传空文件。");
        }
        if (file.sizeBytes() > maximumBytes) {
            throw tooLarge();
        }
        String contentType = MediaTypePolicy.canonical(file.contentType());
        String fileName = displayName(file.fileName());
        ObjectStorage.StoredObject storedObject = null;
        try (var input = new BufferedInputStream(file.source().open(), 64 * 1024)) {
            input.mark(64);
            MediaTypePolicy.verifyPrefix(contentType, input.readNBytes(64));
            input.reset();
            storedObject = storage.put(UPLOAD_BUCKET, input, maximumBytes);
        } catch (StorageLimitExceededException exception) {
            if (storedObject != null) {
                removeNewObject(storedObject);
            }
            throw tooLarge();
        } catch (IOException exception) {
            if (storedObject != null) {
                removeNewObject(storedObject);
            }
            log.warn("Media upload storage failure: workspaceId={}, failureType={}",
                    workspaceId, exception.getClass().getSimpleName());
            throw new BusinessException(503, "MEDIA_STORAGE_UNAVAILABLE", "暂时无法保存文件，请稍后重试。");
        }
        final ObjectStorage.StoredObject object = Objects.requireNonNull(storedObject);

        var mediaId = UUID.randomUUID();
        var objectId = UUID.randomUUID();
        var createdAt = Instant.now(clock);
        var cleanupRegistered = new AtomicBoolean(false);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            return Objects.requireNonNull(transactions.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int completionStatus) {
                        if (completionStatus == STATUS_ROLLED_BACK) {
                            removeNewObject(object);
                        } else if (completionStatus == STATUS_UNKNOWN) {
                            // A commit response can be lost after PostgreSQL has committed.
                            // Retain the bytes for reconciliation rather than deleting a possibly live file.
                            log.error("Unknown media metadata commit outcome: mediaId={}, objectKey={}",
                                    mediaId, object.objectKey());
                        }
                    }
                });
                cleanupRegistered.set(true);
                workspaceAccess.requireEditor(workspaceId, actorId);
                mapper.insertObject(objectId.toString(), workspaceId.toString(), object.backendId(),
                        object.bucket(), object.objectKey(), object.sizeBytes(), object.sha256(), contentType, createdAt);
                mapper.insertVersion(mediaId.toString(), workspaceId.toString(), objectId.toString(),
                        fileName, actorId.toString(), createdAt);
                return new MediaMetadata(mediaId, workspaceId, 1, fileName, contentType,
                        object.sizeBytes(), object.sha256(), MediaStatus.STORED, createdAt);
            }));
        } catch (RuntimeException exception) {
            if (!cleanupRegistered.get()) {
                removeNewObject(object);
            }
            throw exception;
        }
    }

    @Override
    public MediaMetadata get(UUID actorId, UUID workspaceId, UUID mediaId) {
        workspaceAccess.requireMember(workspaceId, actorId);
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            return metadata(requireRow(workspaceId, mediaId));
        }
    }

    @Override
    public List<MediaMetadata> recent(UUID actorId, UUID workspaceId, int limit) {
        workspaceAccess.requireMember(workspaceId, actorId);
        if (limit < 1 || limit > 100) {
            throw BusinessException.badRequest("MEDIA_LIMIT_INVALID", "limit 必须在 1 至 100 之间。");
        }
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            return mapper.recent(workspaceId.toString(), limit).stream().map(MediaService::metadata).toList();
        }
    }

    public Download download(UUID actorId, UUID workspaceId, UUID mediaId) {
        workspaceAccess.requireMember(workspaceId, actorId);
        final MediaRow row;
        try (var ignored = WorkspaceScope.open(workspaceId)) {
            row = requireRow(workspaceId, mediaId);
        }
        if (!storage.backendId().equals(row.backendId())) {
            throw new BusinessException(503, "MEDIA_BACKEND_UNAVAILABLE", "当前实例无法访问该媒体的存储后端。");
        }
        try {
            var stat = storage.stat(row.bucket(), row.objectKey()).orElseThrow(
                    () -> BusinessException.notFound("MEDIA_BYTES_MISSING", "媒体记录存在，但存储文件暂不可用。"));
            if (stat.sizeBytes() != row.sizeBytes()) {
                throw new BusinessException(503, "MEDIA_BYTES_CHANGED", "存储文件与媒体记录不一致，暂时不能下载。");
            }
            return new Download(metadata(row), stat.lastModified(), () -> storage.open(row.bucket(), row.objectKey()));
        } catch (NoSuchFileException exception) {
            throw BusinessException.notFound("MEDIA_BYTES_MISSING", "媒体存储文件暂不可用。");
        } catch (IOException exception) {
            log.warn("Media download storage failure: mediaId={}, failureType={}",
                    mediaId, exception.getClass().getSimpleName());
            throw new BusinessException(503, "MEDIA_STORAGE_UNAVAILABLE", "暂时无法读取媒体文件。");
        }
    }

    private MediaRow requireRow(UUID workspaceId, UUID mediaId) {
        var row = mapper.find(workspaceId.toString(), mediaId.toString());
        if (row == null) {
            throw BusinessException.notFound("MEDIA_NOT_FOUND", "未找到该工作空间中的媒体。");
        }
        return row;
    }

    private void removeNewObject(ObjectStorage.StoredObject object) {
        try {
            storage.delete(object.bucket(), object.objectKey());
        } catch (IOException exception) {
            log.error("New media object cleanup failure: objectKey={}, failureType={}",
                    object.objectKey(), exception.getClass().getSimpleName());
        }
    }

    private static MediaMetadata metadata(MediaRow row) {
        return new MediaMetadata(UUID.fromString(row.mediaId()), UUID.fromString(row.workspaceId()),
                row.versionNumber(), row.fileName(), row.contentType(), row.sizeBytes(),
                row.sha256(), MediaStatus.valueOf(row.status()), row.createdAt());
    }

    private static String displayName(String original) {
        String value = original == null ? "upload" : original.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (value.isBlank()) {
            return "upload";
        }
        return value.codePoints().limit(240).collect(StringBuilder::new,
                StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    private static BusinessException tooLarge() {
        return new BusinessException(413, "MEDIA_TOO_LARGE", "文件超过当前配置的上传大小限制。");
    }

    public record Download(MediaMetadata metadata, Instant lastModified, MediaInput.StreamSource source) {
    }
}
