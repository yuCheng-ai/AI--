package com.yucheng.drama.boot.persistence;

import java.sql.SQLException;
import java.util.List;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class SqlObservationInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(SqlObservationInterceptor.class);
    private final ThreadLocal<Boolean> observing = new ThreadLocal<>();
    private final long slowThresholdMs;
    public SqlObservationInterceptor(@Value("${drama.sql.slow-threshold-ms:300}") long slowThresholdMs) {
        if (slowThresholdMs <= 0) throw new IllegalArgumentException("drama.sql.slow-threshold-ms must be positive.");
        this.slowThresholdMs = slowThresholdMs;
    }
    @Override public Object intercept(Invocation invocation) throws Throwable {
        String statement = ((MappedStatement) invocation.getArgs()[0]).getId();
        // INSERTs that already supply workspace_id can skip MP's getTenantId callback.
        // Enforce the authorized scope independently of SQL rewriting.
        TenantStatementPolicy.requireScope(statement, invocation.getArgs()[1]);
        if (Boolean.TRUE.equals(observing.get())) return invocation.proceed();
        observing.set(true);
        long started = System.nanoTime();
        try {
            Object result = invocation.proceed();
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            int count = result instanceof List<?> list ? list.size() : result instanceof Integer value ? value : -1;
            var event = elapsed >= slowThresholdMs ? log.atWarn() : log.atDebug();
            event.addKeyValue("event", "sql.completed").addKeyValue("statementId", statement)
                .addKeyValue("durationMs", elapsed).addKeyValue("rowCount", count).log("Mapper statement completed");
            return result;
        } catch (Throwable exception) {
            String state = "unknown";
            Throwable cause = exception;
            for (int i = 0; cause != null && i < 16; i++, cause = cause.getCause()) {
                if (cause instanceof SQLException sql) { state = sql.getSQLState(); break; }
            }
            log.atWarn().addKeyValue("event", "sql.failed").addKeyValue("statementId", statement)
                .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
                .addKeyValue("sqlState", state).log("Mapper statement failed");
            throw exception;
        } finally { observing.remove(); }
    }
}
