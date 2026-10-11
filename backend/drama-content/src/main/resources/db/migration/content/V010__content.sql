CREATE SCHEMA IF NOT EXISTS content;

CREATE TABLE content.project (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    description VARCHAR(4000) NOT NULL DEFAULT '',
    aspect_ratio VARCHAR(10) NOT NULL DEFAULT '9:16' CHECK (aspect_ratio IN ('9:16', '16:9', '1:1')),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMPTZ,
    UNIQUE (workspace_id, id)
);
CREATE INDEX project_workspace_created_idx ON content.project(workspace_id, created_at, id) WHERE archived_at IS NULL;

CREATE TABLE content.shot (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    shot_number INTEGER NOT NULL CHECK (shot_number > 0),
    current_revision BIGINT NOT NULL CHECK (current_revision > 0),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workspace_id, id),
    UNIQUE (workspace_id, project_id, shot_number),
    FOREIGN KEY (workspace_id, project_id) REFERENCES content.project(workspace_id, id)
);

CREATE TABLE content.shot_revision (
    workspace_id UUID NOT NULL,
    shot_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    title VARCHAR(120) NOT NULL CHECK (length(btrim(title)) > 0),
    description VARCHAR(12000) NOT NULL DEFAULT '',
    dialogue VARCHAR(8000) NOT NULL DEFAULT '',
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds BETWEEN 1 AND 60),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, shot_id, revision),
    FOREIGN KEY (workspace_id, shot_id) REFERENCES content.shot(workspace_id, id)
);
ALTER TABLE content.shot ADD CONSTRAINT shot_current_revision_fk
    FOREIGN KEY (workspace_id, id, current_revision)
    REFERENCES content.shot_revision(workspace_id, shot_id, revision)
    DEFERRABLE INITIALLY DEFERRED;
