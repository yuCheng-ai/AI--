package com.yucheng.drama.boot.persistence;

import com.yucheng.drama.kernel.api.WorkspaceScope;
import java.util.Map;
import java.util.Set;

/** Reviewed bypass list shared by startup validation and the execution guard. */
public final class TenantStatementPolicy {
    private TenantStatementPolicy() {}
    public static final Set<String> GLOBAL_STATEMENTS = Set.of(
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.findUserByUsername",
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.findUserById",
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.insertUser",
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.listForActor",
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.findForActor",
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.findRole",
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.insertWorkspace",
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.insertAdminMembership",
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.renameWorkspace",
        "com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper.archiveWorkspace"
    );

    public static void requireScope(String statementId, Object parameter) {
        if (GLOBAL_STATEMENTS.contains(statementId)) return;
        var workspaceId = WorkspaceScope.currentRequired();
        if (parameter instanceof Map<?, ?> values && values.containsKey("workspaceId")) {
            Object supplied = values.get("workspaceId");
            if (supplied == null || !workspaceId.toString().equals(supplied.toString())) {
                throw new IllegalStateException("SQL workspace parameter differs from the authorized scope");
            }
        }
    }
}
