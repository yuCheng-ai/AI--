CREATE SCHEMA IF NOT EXISTS media;

CREATE TABLE media.physical_object (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    backend_id VARCHAR(64) NOT NULL,
    bucket VARCHAR(64) NOT NULL,
    object_key VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    content_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT physical_object_workspace_id_unique UNIQUE (workspace_id, id),
    CONSTRAINT physical_object_location_unique UNIQUE (backend_id, bucket, object_key)
);

CREATE TABLE media.media_version (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    object_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('STORED')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT media_version_workspace_id_unique UNIQUE (workspace_id, id),
    CONSTRAINT media_version_object_workspace_fk FOREIGN KEY (workspace_id, object_id)
        REFERENCES media.physical_object (workspace_id, id)
);

CREATE INDEX media_version_workspace_recent_idx
    ON media.media_version (workspace_id, created_at DESC, id DESC);
CREATE INDEX media_version_object_idx ON media.media_version (workspace_id, object_id);

COMMENT ON TABLE media.media_version IS 'Immutable uploaded media versions. STORED does not mean probed, transcoded, or render ready.';
COMMENT ON TABLE media.physical_object IS 'Physical storage location, separate from logical media version metadata.';
