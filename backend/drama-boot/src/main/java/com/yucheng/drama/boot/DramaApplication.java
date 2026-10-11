package com.yucheng.drama.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.yucheng.drama")
@ConfigurationPropertiesScan(basePackages = "com.yucheng.drama")
public class DramaApplication {
    public static void main(String[] args) {
        SpringApplication.run(DramaApplication.class, args);
    }
}
