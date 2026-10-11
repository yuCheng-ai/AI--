package com.yucheng.drama.media.internal.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaMapper {
    String PROJECTION = """
            SELECT v.id::text AS media_id, v.workspace_id::text AS workspace_id,
                   o.id::text AS object_id, o.backend_id, o.bucket, o.object_key,
                   v.file_name, o.content_type, o.size_bytes, o.sha256,
                   v.status, v.version_number, v.created_at
              FROM media.media_version v
              JOIN media.physical_object o ON o.workspace_id = v.workspace_id AND o.id = v.object_id
            """;

    @Insert("""
            INSERT INTO media.physical_object
                (id, workspace_id, backend_id, bucket, object_key, size_bytes, sha256, content_type, created_at)
            VALUES (CAST(#{objectId} AS uuid), CAST(#{workspaceId} AS uuid), #{backendId}, #{bucket},
                    #{objectKey}, #{sizeBytes}, #{sha256}, #{contentType}, #{createdAt})
            """)
    int insertObject(@Param("objectId") String objectId, @Param("workspaceId") String workspaceId,
            @Param("backendId") String backendId, @Param("bucket") String bucket,
            @Param("objectKey") String objectKey, @Param("sizeBytes") long sizeBytes,
            @Param("sha256") String sha256, @Param("contentType") String contentType,
            @Param("createdAt") Instant createdAt);

    @Insert("""
            INSERT INTO media.media_version
                (id, workspace_id, object_id, version_number, file_name, status, created_by, created_at)
            VALUES (CAST(#{mediaId} AS uuid), CAST(#{workspaceId} AS uuid), CAST(#{objectId} AS uuid),
                    1, #{fileName}, 'STORED', CAST(#{actorId} AS uuid), #{createdAt})
            """)
    int insertVersion(@Param("mediaId") String mediaId, @Param("workspaceId") String workspaceId,
            @Param("objectId") String objectId, @Param("fileName") String fileName,
            @Param("actorId") String actorId, @Param("createdAt") Instant createdAt);

    @Select(PROJECTION + " WHERE v.workspace_id = CAST(#{workspaceId} AS uuid) AND v.id = CAST(#{mediaId} AS uuid)")
    @Results(id = "mediaRow")
    @ConstructorArgs({
        @Arg(column = "media_id", javaType = String.class),
        @Arg(column = "workspace_id", javaType = String.class),
        @Arg(column = "object_id", javaType = String.class),
        @Arg(column = "backend_id", javaType = String.class),
        @Arg(column = "bucket", javaType = String.class),
        @Arg(column = "object_key", javaType = String.class),
        @Arg(column = "file_name", javaType = String.class),
        @Arg(column = "content_type", javaType = String.class),
        @Arg(column = "size_bytes", javaType = long.class),
        @Arg(column = "sha256", javaType = String.class),
        @Arg(column = "status", javaType = String.class),
        @Arg(column = "version_number", javaType = int.class),
        @Arg(column = "created_at", javaType = Instant.class)
    })
    MediaRow find(@Param("workspaceId") String workspaceId, @Param("mediaId") String mediaId);

    @Select(PROJECTION + """
             WHERE v.workspace_id = CAST(#{workspaceId} AS uuid)
             ORDER BY v.created_at DESC, v.id DESC LIMIT #{limit}
            """)
    @ResultMap("mediaRow")
    List<MediaRow> recent(@Param("workspaceId") String workspaceId, @Param("limit") int limit);
}
