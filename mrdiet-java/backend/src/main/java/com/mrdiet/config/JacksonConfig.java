package com.mrdiet.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * The React app was written against PHP, which sent dates as plain strings
 * ("2026-05-01 10:30:00", "2026-05-01", "08:00:00"). These serializers keep that
 * exact format so no page has to change.
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer phpCompatibleDates() {
        return builder -> builder
                .serializerByType(java.sql.Timestamp.class,
                        new AsString<>(java.sql.Timestamp.class, t -> t.toLocalDateTime().format(DATE_TIME)))
                .serializerByType(java.sql.Date.class,
                        new AsString<>(java.sql.Date.class, d -> d.toLocalDate().toString()))
                .serializerByType(java.sql.Time.class,
                        new AsString<>(java.sql.Time.class, t -> t.toLocalTime().format(TIME)))
                .serializerByType(LocalDateTime.class,
                        new AsString<>(LocalDateTime.class, t -> t.format(DATE_TIME)))
                .serializerByType(OffsetDateTime.class,
                        new AsString<>(OffsetDateTime.class, t -> t.toLocalDateTime().format(DATE_TIME)))
                .serializerByType(LocalDate.class,
                        new AsString<>(LocalDate.class, LocalDate::toString))
                .serializerByType(LocalTime.class,
                        new AsString<>(LocalTime.class, t -> t.format(TIME)));
    }

    /** Writes any value as a string produced by the given function. */
    static final class AsString<T> extends StdSerializer<T> {
        private final transient Function<T, String> fn;

        AsString(Class<T> type, Function<T, String> fn) {
            super(type);
            this.fn = fn;
        }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(fn.apply(value));
        }
    }
}
