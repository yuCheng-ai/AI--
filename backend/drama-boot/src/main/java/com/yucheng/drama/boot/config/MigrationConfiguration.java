package com.yucheng.drama.boot.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "drama.runtime.role", havingValue = "migrate")
class MigrationConfiguration {
    @Bean ApplicationRunner migrationExit(ConfigurableApplicationContext context) {
        return args -> SpringApplication.exit(context, () -> 0);
    }
}
