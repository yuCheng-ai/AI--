package com.yucheng.drama.workspace.internal.application;

import com.yucheng.drama.workspace.internal.infrastructure.persistence.WorkspaceMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Explicit, one-time bootstrap only. Existing users and passwords are never overwritten. */
@Component
@ConditionalOnProperty(prefix = "drama.bootstrap", name = "enabled", havingValue = "true")
public class InitialAdministrator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialAdministrator.class);
    private final WorkspaceMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public InitialAdministrator(WorkspaceMapper mapper, PasswordEncoder passwordEncoder,
                                @Value("${drama.bootstrap.username:}") String username,
                                @Value("${drama.bootstrap.password:}") String password) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username.strip().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!username.matches("[a-z0-9._@-]{3,64}")) {
            throw new IllegalStateException("Explicit bootstrap username must contain 3–64 letters, digits, '.', '_', '@' or '-'");
        }
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.isBlank() || password.length() < 12 || passwordBytes > 72) {
            throw new IllegalStateException("Explicit bootstrap password must contain at least 12 characters and at most 72 UTF-8 bytes");
        }
        if (mapper.findUserByUsername(username) != null) {
            log.info("event=bootstrap.user.already_exists outcome=SKIPPED");
            return;
        }
        int inserted = mapper.insertUser(UUID.randomUUID(), username, passwordEncoder.encode(password));
        log.info("event=bootstrap.user.initialized outcome={}", inserted == 1 ? "CREATED" : "ALREADY_EXISTS");
    }
}
