package com.fluxguard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Envío de correos con la API HTTP de Resend (POST https://api.resend.com/emails): https://resend.com/docs/api-reference/emails/send-email
 * Usa HTTPS, así que funciona en el plan gratuito de Render (que bloquea los puertos SMTP).
 * Lee RESEND_API_KEY y el remitente de RESEND_FROM_EMAIL (o, si no existe, de BREVO_SENDER_EMAIL).
 * El dominio del remitente debe estar verificado en Resend (Domains).
 */
@Service
public class ResendEmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String remitente;

    public ResendEmailService(
            @Value("${resend.api-key:${RESEND_API_KEY:}}") String apiKey,
            @Value("${resend.from-email:${RESEND_FROM_EMAIL:${BREVO_SENDER_EMAIL:}}}") String fromEmail,
            @Value("${resend.from-name:${BREVO_SENDER_NAME:FluxGuard}}") String fromName) {
        this.apiKey = apiKey.trim();
        this.remitente = fromName.trim().isEmpty() ? fromEmail.trim() : fromName.trim() + " <" + fromEmail.trim() + ">";

        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(5_000);
        tiempos.setReadTimeout(15_000);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(tiempos)
                .defaultHeader("Authorization", "Bearer " + this.apiKey)
                .build();

        if (estaConfigurado()) {
            if (fromEmail.isBlank()) {
                log.warn("[FluxG] RESEND_API_KEY está configurada pero falta el remitente (RESEND_FROM_EMAIL o BREVO_SENDER_EMAIL).");
            } else {
                log.info("[FluxG] Los correos se enviarán con Resend desde {}.", remitente);
            }
        }
    }

    public boolean estaConfigurado() {
        return !apiKey.isEmpty();
    }

    /** Envía el correo con el enlace de verificación. Lanza RestClientException si Resend rechaza el envío. */
    public void enviarEnlaceVerificacion(String correo, String nombre, String enlace, int horasVigencia) {
        enviar(new Correo(correo, nombre, PlantillaVerificacion.ASUNTO,
                PlantillaVerificacion.html(nombre, enlace, horasVigencia),
                PlantillaVerificacion.texto(nombre, enlace, horasVigencia), null));
    }

    /** Envía un correo cualquiera, con Reply-To y adjunto opcionales. Lanza RestClientException si Resend lo rechaza. */
    public void enviar(Correo correo) {
        Map<String, Object> body = new HashMap<>();
        body.put("from", remitente);
        body.put("to", List.of(correo.para()));
        body.put("subject", correo.asunto());
        body.put("html", correo.html());
        body.put("text", correo.texto());
        if (correo.responderA() != null) {
            body.put("reply_to", correo.responderA());
        }
        if (correo.adjunto() != null) {
            body.put("attachments", List.of(Map.of("filename", correo.adjunto().nombre(), "content", correo.adjunto().base64())));
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[FluxG] Correo \"{}\" enviado con Resend a {}", correo.asunto(), correo.para());
        } catch (RestClientResponseException e) {
            // 403 suele ser dominio sin verificar; 401, API key incorrecta; 422, remitente o datos inválidos
            log.error("[FluxG] Resend rechazó el correo a {} ({}): {}", correo.para(), e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            log.error("[FluxG] No se pudo conectar con Resend para enviar a {}: {}", correo.para(), e.getMessage());
            throw e;
        }
    }
}
