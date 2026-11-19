package com.mrdiet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;

/** Serves uploaded logos / payout screenshots at /api/uploads/** (was api/uploads/ in PHP). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path uploadDir;

    public WebConfig(AppProperties props) throws Exception {
        this.uploadDir = Path.of(props.uploadDir() == null ? "./uploads" : props.uploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadDir.toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/api/uploads/**").addResourceLocations(location);
    }
}
