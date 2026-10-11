package com.yucheng.drama.media.internal.application.port;

import java.io.IOException;

public final class StorageLimitExceededException extends IOException {
    public StorageLimitExceededException() {
        super("The uploaded file exceeds the configured size limit.");
    }
}
