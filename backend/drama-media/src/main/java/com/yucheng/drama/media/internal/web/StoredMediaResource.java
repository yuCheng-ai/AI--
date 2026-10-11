package com.yucheng.drama.media.internal.web;

import com.yucheng.drama.media.internal.application.MediaService.Download;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.AbstractResource;

/** Repeatable resource: Spring MVC can open and seek each requested byte range. */
public final class StoredMediaResource extends AbstractResource {
    private final Download download;

    public StoredMediaResource(Download download) {
        this.download = download;
    }

    @Override
    public String getDescription() {
        return "Authorized stored media resource";
    }

    @Override
    public String getFilename() {
        return download.metadata().fileName();
    }

    @Override
    public long contentLength() {
        return download.metadata().sizeBytes();
    }

    @Override
    public long lastModified() {
        return download.lastModified().toEpochMilli();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return download.source().open();
    }
}
