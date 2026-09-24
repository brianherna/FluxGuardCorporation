package com.fluxguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@SpringBootApplication
public class FluxGApplication {

    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(FluxGApplication.class, args);
    }

    /**
     * Carga variables de entorno desde el archivo local .env en System properties
     * para que Spring Boot y application.properties puedan resolverlas automáticamente.
     */
    private static void loadEnv() {
        File envFile = new File(".env");
        if (envFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(envFile.toPath());
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int equalsIdx = trimmed.indexOf('=');
                    if (equalsIdx > 0) {
                        String key = trimmed.substring(0, equalsIdx).trim();
                        String value = trimmed.substring(equalsIdx + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
                System.out.println("[FluxG] Variables de configuración cargadas exitosamente desde archivo local .env");
            } catch (Exception e) {
                System.err.println("[FluxG] Advertencia: No se pudo leer el archivo .env: " + e.getMessage());
            }
        }
    }
}
