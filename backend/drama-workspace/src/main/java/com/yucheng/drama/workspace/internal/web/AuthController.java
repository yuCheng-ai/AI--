package com.yucheng.drama.workspace.internal.web;

import com.yucheng.drama.kernel.api.BusinessException;
import com.yucheng.drama.workspace.api.AuthenticatedUser;
import com.yucheng.drama.workspace.api.WorkspaceDtos.*;
import com.yucheng.drama.workspace.internal.application.CurrentUserQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/auth")
@Tag(name = "认证", description = "Session 登录；写请求必须携带 CSRF 令牌")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final CurrentUserQuery currentUser;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository, CurrentUserQuery currentUser) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    @Operation(summary = "使用用户名和密码登录", description = "先 GET /auth/csrf，再携带令牌登录。成功后会轮换 Session ID 并失效旧 CSRF，必须重新获取令牌。")
    public LoginResponse login(@Valid @RequestBody LoginRequest command,
                               HttpServletRequest request, HttpServletResponse response) {
        var authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                command.username().strip().toLowerCase(Locale.ROOT), command.password()));
        request.getSession(true);
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        csrfTokenRepository.saveToken(null, request, response);
        var principal = (AuthenticatedUser) authentication.getPrincipal();
        return new LoginResponse(new UserView(principal.userId(), principal.getUsername()), true);
    }

    @GetMapping("/me")
    @Operation(summary = "读取当前登录用户")
    public UserView me(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(401, "UNAUTHENTICATED", "请先登录");
        return currentUser.find(principal.userId());
    }

    @GetMapping("/csrf")
    @Operation(summary = "获取或刷新 CSRF 令牌", description = "登录前、登录成功后及退出后需要重新获取。按返回的 headerName 发送 token。")
    public CsrfResponse csrf(@Parameter(hidden = true) CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 256) String password) {
        @Override public String toString() { return "LoginRequest[credentials redacted]"; }
    }
}
