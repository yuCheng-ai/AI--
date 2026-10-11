package com.yucheng.drama.workspace.internal.application;

import com.yucheng.drama.kernel.api.BusinessException;
import com.yucheng.drama.workspace.api.WorkspaceDtos.UserView;
import com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserQuery {
    private final WorkspaceMapper mapper;

    public CurrentUserQuery(WorkspaceMapper mapper) { this.mapper = mapper; }

    @Transactional(readOnly = true)
    public UserView find(UUID userId) {
        var user = mapper.findUserById(userId);
        if (user == null || !user.enabled()) throw new BusinessException(401, "UNAUTHENTICATED", "用户已停用，请重新登录");
        return new UserView(UUID.fromString(user.id()), user.username());
    }
}
