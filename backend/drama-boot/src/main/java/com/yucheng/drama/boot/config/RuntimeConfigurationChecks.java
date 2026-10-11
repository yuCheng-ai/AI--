package com.yucheng.drama.boot.config;

import com.yucheng.drama.media.internal.config.MediaStorageProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Validate settings that span framework and module configuration before accepting traffic. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class RuntimeConfigurationChecks {
    @Bean SmartInitializingSingleton validateRuntimeSettings(MediaStorageProperties storage,
            MultipartProperties multipart, Environment environment) {
        return () -> {
            long maximumBytes = storage.maxFileSize().toBytes();
            if (multipart.getMaxFileSize().toBytes() != maximumBytes) {
                throw new IllegalStateException("Configure file size through drama.storage.max-file-size only.");
            }
            if (multipart.getMaxRequestSize().toBytes() <= maximumBytes) {
                throw new IllegalStateException("Multipart max-request-size must exceed max-file-size to allow multipart overhead.");
            }
            int maxPool = environment.getRequiredProperty("spring.datasource.hikari.maximum-pool-size", Integer.class);
            int minPool = environment.getRequiredProperty("spring.datasource.hikari.minimum-idle", Integer.class);
            if (maxPool < 1 || minPool < 0 || minPool > maxPool) {
                throw new IllegalStateException("Database pool requires 0 <= minimum-idle <= maximum-pool-size and a positive maximum.");
            }
        };
    }
}
