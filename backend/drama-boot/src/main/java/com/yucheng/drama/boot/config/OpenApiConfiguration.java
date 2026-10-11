package com.yucheng.drama.boot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    @Bean OpenAPI dramaOpenApi() {
        return new OpenAPI()
            .path("/api/v1/auth/logout", new PathItem().post(new Operation()
                .operationId("logout").addTagsItem("认证").summary("退出当前会话")
                .description("由 Spring Security 处理，需要有效 CSRF 令牌；成功后清理 Session 和 Cookie。")
                .responses(new ApiResponses().addApiResponse("204", new ApiResponse().description("已退出"))
                    .addApiResponse("403", new ApiResponse().description("CSRF 校验失败")))))
            .info(new Info().title("AI 短剧制作系统 · 后端接口")
            .version("0.1.0")
            .description("先调用 GET /api/v1/auth/csrf，再 POST /api/v1/auth/login。登录后再次获取 CSRF，再执行写操作。Swagger 同源使用 Session Cookie，并自动携带 XSRF-TOKEN。工作空间权限在每次请求中重新验证。"
                + "\n\n时间约定：发生时刻输出 UTC，例如 2026-10-11T12:30:00.123456Z；输入须带 Z 或明确偏移（如 +08:00），最多 6 位小数秒。纯日期使用 yyyy-MM-dd，不转换时区。JSON 请求体和 URL 参数使用相同规则；URL 中的加号应编码为 %2B。"))
            .schemaRequirement("session", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE).name("DRAMA_SESSION"));
    }
}
