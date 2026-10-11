package com.yucheng.drama.media.internal.application.port;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

/** Physical storage only. Authorization and media metadata belong to the application layer. */
public interface ObjectStorage {
    String backendId();

    StoredObject put(String bucket, InputStream input, long maximumBytes) throws IOException;

    InputStream open(String bucket, String objectKey) throws IOException;

    Optional<ObjectStat> stat(String bucket, String objectKey) throws IOException;

    void delete(String bucket, String objectKey) throws IOException;

    record StoredObject(String backendId, String bucket, String objectKey, long sizeBytes, String sha256) {
    }

    record ObjectStat(long sizeBytes, Instant lastModified) {
    }
}
