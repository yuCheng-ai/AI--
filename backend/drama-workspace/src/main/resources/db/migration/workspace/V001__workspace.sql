CREATE SCHEMA IF NOT EXISTS workspace;

CREATE TABLE workspace.app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT app_user_username_format CHECK (username ~ '^[a-z0-9._@-]{3,64}$')
);

CREATE TABLE workspace.workspace (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    created_by UUID NOT NULL REFERENCES workspace.app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMPTZ
);

CREATE TABLE workspace.membership (
    workspace_id UUID NOT NULL REFERENCES workspace.workspace(id),
    user_id UUID NOT NULL REFERENCES workspace.app_user(id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'EDITOR', 'REVIEWER', 'VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX membership_user_workspace_idx ON workspace.membership(user_id, workspace_id);
CREATE INDEX workspace_active_created_idx ON workspace.workspace(created_at, id) WHERE archived_at IS NULL;
