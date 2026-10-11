package com.yucheng.drama.boot.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Request-ID");
        String correlationId = supplied != null && supplied.matches("[A-Za-z0-9_-]{8,64}")
            ? supplied : UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        long started = System.nanoTime();
        try (MDC.MDCCloseable ignored1 = MDC.putCloseable("traceId", traceId);
             MDC.MDCCloseable ignored2 = MDC.putCloseable("correlationId", correlationId)) {
            response.setHeader("X-Request-ID", correlationId);
            response.setHeader("X-Trace-ID", traceId);
            try { chain.doFilter(request, response); }
            finally {
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                log.atInfo().addKeyValue("event", "http.completed")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("route", route == null ? "unmatched" : route.toString())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
                    .log("HTTP request completed");
            }
        }
    }
}
