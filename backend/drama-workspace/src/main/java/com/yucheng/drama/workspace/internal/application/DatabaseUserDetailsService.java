package com.yucheng.drama.workspace.internal.application;

import com.yucheng.drama.workspace.api.AuthenticatedUser;
import com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final WorkspaceMapper mapper;

    public DatabaseUserDetailsService(WorkspaceMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = mapper.findUserByUsername(username.strip().toLowerCase(Locale.ROOT));
        if (user == null) throw new UsernameNotFoundException("Invalid credentials");
        return new AuthenticatedUser(UUID.fromString(user.id()), user.username(), user.passwordHash(), user.enabled());
    }
}
