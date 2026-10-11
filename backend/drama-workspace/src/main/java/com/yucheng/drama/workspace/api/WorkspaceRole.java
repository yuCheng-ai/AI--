package com.yucheng.drama.workspace.api;

public enum WorkspaceRole {
    ADMIN, EDITOR, REVIEWER, VIEWER;

    public boolean canEdit() {
        return this == ADMIN || this == EDITOR;
    }
}
