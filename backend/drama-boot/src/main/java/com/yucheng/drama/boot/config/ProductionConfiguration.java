package com.yucheng.drama.boot.config;

import java.nio.file.Path;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
class ProductionConfiguration {
    /** Run before storage and datasource constructors can create files or open connections. */
    @Bean static BeanFactoryPostProcessor validateProductionLocations(Environment environment) {
        return beanFactory -> {
            String databaseUrl = required(environment, "spring.datasource.url");
            if (!databaseUrl.startsWith("jdbc:postgresql:")) {
                throw new IllegalStateException("Production requires an explicit PostgreSQL JDBC URL.");
            }
            if (!"migrate".equals(environment.getProperty("drama.runtime.role"))) {
                String root = required(environment, "drama.storage.local.root");
                if (!Path.of(root).isAbsolute()) {
                    throw new IllegalStateException("Production media storage must use an absolute path.");
                }
            }
        };
    }

    private static String required(Environment environment, String key) {
        String value = environment.resolveRequiredPlaceholders(environment.getRequiredProperty(key));
        if (value.isBlank()) throw new IllegalStateException("Production setting must not be blank: " + key);
        return value;
    }
}
