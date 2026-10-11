package com.yucheng.drama.media.internal.application;

import java.io.IOException;
import java.io.InputStream;

/** A repeatable stream source, independent of HTTP multipart and local file paths. */
public record MediaInput(String fileName, String contentType, long sizeBytes, StreamSource source) {
    @FunctionalInterface
    public interface StreamSource {
        InputStream open() throws IOException;
    }
}
