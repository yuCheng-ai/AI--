package com.yucheng.drama.boot.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfiguration {
    @Bean Clock applicationClock() {
        // PostgreSQL stores timestamps at microsecond precision.
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
