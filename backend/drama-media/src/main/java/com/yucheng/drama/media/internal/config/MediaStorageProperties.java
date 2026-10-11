package com.yucheng.drama.media.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("drama.storage")
public record MediaStorageProperties(DataSize maxFileSize, Local local) {
    public MediaStorageProperties {
        if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("drama.storage.max-file-size must be positive.");
        }
        if (local == null || local.root() == null || local.root().isBlank()) {
            throw new IllegalArgumentException("drama.storage.local.root must not be blank.");
        }
    }

    public record Local(String root) {
    }
}
