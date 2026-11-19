package com.mrdiet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * Mr. Diet API — Spring Boot replacement for the old PHP "api/" folder.
 *
 * Run locally:   mvn spring-boot:run
 * Build a jar:   mvn clean package   →  target/mrdiet-api.jar
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class MrDietApplication {

    public static void main(String[] args) {
        // All "today" / plan-date logic runs in Indian time, same as the PHP cron did.
        String tz = System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Kolkata");
        TimeZone.setDefault(TimeZone.getTimeZone(tz));
        SpringApplication.run(MrDietApplication.class, args);
    }
}
