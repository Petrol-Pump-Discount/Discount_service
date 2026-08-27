package com.petrolpump.discount.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] originPatterns;

    public WebConfig(@Value("${app.cors.origins:https://nss01.com,https://www.nss01.com,http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000}") String originsCsv) {
        this.originPatterns = ArraysSafe.split(originsCsv);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(originPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false);
    }

    private static final class ArraysSafe {
        static String[] split(String csv) {
            if (csv == null || csv.isBlank()) {
                return new String[]{"https://nss01.com", "https://www.nss01.com"};
            }
            return java.util.Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }
    }
}
