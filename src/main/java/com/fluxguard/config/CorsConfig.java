package com.fluxguard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/** Solo los orígenes de app.cors.allowed-origins pueden llamar a la API desde otro dominio. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origenesPermitidos;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:8080}") String origenes) {
        this.origenesPermitidos = Arrays.stream(origenes.split(","))
                .map(String::trim)
                .map(origen -> origen.replaceAll("/+$", ""))
                .filter(origen -> !origen.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origenesPermitidos)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Admin-Key");
    }
}
