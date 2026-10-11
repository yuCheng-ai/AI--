package com.yucheng.drama.boot.config;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.yucheng.drama.kernel.api.WorkspaceScope;
import static com.yucheng.drama.boot.persistence.TenantStatementPolicy.GLOBAL_STATEMENTS;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.yucheng.drama", annotationClass = Mapper.class)
class PersistenceConfiguration {
    @Bean MybatisPlusInterceptor workspaceInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new StringValue(WorkspaceScope.currentRequired().toString()); }
            @Override public String getTenantIdColumn() { return "workspace_id"; }
        }));
        return interceptor;
    }
    @Bean SmartInitializingSingleton verifyTenantBypasses(SqlSessionFactory factory) {
        return () -> {
            var configuration = factory.getConfiguration();
            for (String id : GLOBAL_STATEMENTS) {
                if (!configuration.hasStatement(id)) throw new IllegalStateException("Missing reviewed global statement: " + id);
            }
            for (Class<?> mapper : configuration.getMapperRegistry().getMappers()) {
                if (mapper.isAnnotationPresent(InterceptorIgnore.class)) throw new IllegalStateException("Class-level SQL bypass is forbidden: " + mapper.getName());
                for (var method : mapper.getMethods()) {
                    InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);
                    if (ignore != null && !"false".equals(ignore.tenantLine()) && !"0".equals(ignore.tenantLine()) && !ignore.tenantLine().isEmpty()) {
                        String id = mapper.getName() + "." + method.getName();
                        if (!GLOBAL_STATEMENTS.contains(id)) throw new IllegalStateException("Unreviewed global statement: " + id);
                    }
                }
            }
        };
    }
}
