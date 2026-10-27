package com.fluxguard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Envío de correos transaccionales con la API HTTP de Brevo (POST https://api.brevo.com/v3/smtp/email).
 * Lee BREVO_API_KEY, BREVO_SENDER_EMAIL y BREVO_SENDER_NAME del archivo .env o de variables de entorno.
 */
@Service
public class BrevoEmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailService(
            @Value("${brevo.api-key:${BREVO_API_KEY:}}") String apiKey,
            @Value("${brevo.sender.email:${BREVO_SENDER_EMAIL:}}") String senderEmail,
            @Value("${brevo.sender.name:${BREVO_SENDER_NAME:FluxGuard}}") String senderName) {
        this.apiKey = apiKey.trim();
        this.senderEmail = senderEmail.trim();
        this.senderName = senderName.trim();
        this.restClient = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", this.apiKey)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();

        if (!estaConfigurado()) {
            log.warn("[FluxG] Brevo no está configurado (BREVO_API_KEY / BREVO_SENDER_EMAIL). "
                    + "Los enlaces de verificación se mostrarán solo en la consola del servidor.");
        }
    }

    public boolean estaConfigurado() {
        return !apiKey.isEmpty() && !senderEmail.isEmpty();
    }

    /**
     * Envía el correo con el enlace de verificación. Lanza RestClientException si Brevo rechaza el envío.
     */
    public void enviarEnlaceVerificacion(String correo, String nombre, String enlace, int horasVigencia) {
        if (!estaConfigurado()) {
            log.warn("[FluxG] (modo desarrollo) Enlace de verificación para {}: {}", correo, enlace);
            return;
        }
        enviar(correo, nombre, PlantillaVerificacion.ASUNTO, PlantillaVerificacion.html(nombre, enlace, horasVigencia),
                PlantillaVerificacion.texto(nombre, enlace, horasVigencia), null, "de verificación");
    }

    /**
     * Envía un correo cualquiera, con un adjunto opcional. Sin Brevo configurado solo lo anota en la consola.
     * Lanza RestClientException si Brevo rechaza el envío.
     */
    public void enviar(String correo, String nombre, String asunto, String html, String texto, Adjunto adjunto, String descripcion) {
        if (!estaConfigurado()) {
            log.warn("[FluxG] (modo desarrollo) Correo {} para {} no enviado: Brevo no está configurado.", descripcion, correo);
            return;
        }

        Map<String, Object> body = new HashMap<>(Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", correo, "name", nombre == null || nombre.isBlank() ? correo : nombre)),
                "subject", asunto,
                "htmlContent", html,
                "textContent", texto
        ));
        if (adjunto != null) {
            body.put("attachment", List.of(Map.of("name", adjunto.nombre(), "content", adjunto.base64())));
        }

        try {
            restClient.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[FluxG] Brevo aceptó el correo {} para {} (si no llega, revisa Transactional > Logs en Brevo)", descripcion, correo);
        } catch (RestClientResponseException e) {
            log.error("[FluxG] Brevo rechazó el correo a {} ({}): {}", correo, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            log.error("[FluxG] No se pudo conectar con Brevo para enviar a {}: {}", correo, e.getMessage());
            throw e;
        }
    }
}
