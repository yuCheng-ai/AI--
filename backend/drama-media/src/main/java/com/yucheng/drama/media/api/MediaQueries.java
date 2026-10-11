package com.yucheng.drama.media.api;

import java.util.List;
import java.util.UUID;

public interface MediaQueries {
    MediaMetadata get(UUID actorId, UUID workspaceId, UUID mediaId);

    List<MediaMetadata> recent(UUID actorId, UUID workspaceId, int limit);
}
