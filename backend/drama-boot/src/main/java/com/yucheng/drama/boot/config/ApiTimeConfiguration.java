package com.yucheng.drama.boot.config;

import com.yucheng.drama.boot.web.ApiTimeFormats;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration(proxyBeanMethods = false)
class ApiTimeConfiguration implements WebMvcConfigurer {
    @Bean SimpleModule apiTimeModule() {
        var module = new SimpleModule("drama-api-time");
        module.addSerializer(Instant.class, new TimeSerializer<>(ApiTimeFormats::formatInstant));
        module.addDeserializer(Instant.class, new TimeDeserializer<>(Instant.class, ApiTimeFormats::parseInstant));
        module.addSerializer(LocalDate.class, new TimeSerializer<>(ApiTimeFormats::formatDate));
        module.addDeserializer(LocalDate.class, new TimeDeserializer<>(LocalDate.class, ApiTimeFormats::parseDate));
        return module;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Instant.class, ApiTimeFormats::parseInstant);
        registry.addConverter(String.class, LocalDate.class, ApiTimeFormats::parseDate);
    }

    private static final class TimeSerializer<T> extends ValueSerializer<T> {
        private final Function<T, String> formatter;

        private TimeSerializer(Function<T, String> formatter) {
            this.formatter = formatter;
        }

        @Override
        public void serialize(T value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(formatter.apply(value));
        }
    }

    private static final class TimeDeserializer<T> extends ValueDeserializer<T> {
        private final Class<T> type;
        private final Function<String, T> parser;

        private TimeDeserializer(Class<T> type, Function<String, T> parser) {
            this.type = type;
            this.parser = parser;
        }

        @Override
        public T deserialize(JsonParser input, DeserializationContext context) {
            if (!input.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(type, "Date and time values must be JSON strings.");
            }
            try {
                return parser.apply(input.getString());
            } catch (DateTimeException | IllegalArgumentException exception) {
                // Do not echo the request value in the error or logs.
                return context.reportInputMismatch(type, "Invalid date or timestamp format.");
            }
        }
    }
}
