package com.fluxguard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

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

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", correo, "name", nombre)),
                "subject", "Verifica tu correo en FluxGuard",
                "htmlContent", plantillaVerificacion(nombre, enlace, horasVigencia),
                "textContent", "Hola " + nombre + ",\n\nAbre este enlace para activar tu cuenta de FluxGuard:\n"
                        + enlace + "\n\nEl enlace vence en " + horasVigencia + " horas."
                        + "\n\nSi no creaste esta cuenta, ignora este correo."
        );

        try {
            restClient.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[FluxG] Correo de verificación enviado con Brevo a {}", correo);
        } catch (RestClientResponseException e) {
            log.error("[FluxG] Brevo rechazó el correo a {} ({}): {}", correo, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            log.error("[FluxG] No se pudo conectar con Brevo para enviar a {}: {}", correo, e.getMessage());
            throw e;
        }
    }

    private String plantillaVerificacion(String nombre, String enlace, int horasVigencia) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <body style="margin:0;padding:0;background:#0b0812;font-family:Arial,Helvetica,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0b0812;padding:32px 12px;">
                    <tr><td align="center">
                      <table width="520" cellpadding="0" cellspacing="0" style="max-width:520px;width:100%%;background:#120c1e;border:1px solid #2c2342;">
                        <tr><td style="padding:28px 32px 8px;color:#b9a7ff;font-size:12px;font-weight:bold;letter-spacing:3px;">FLUXGUARD / VERIFICACIÓN</td></tr>
                        <tr><td style="padding:8px 32px;color:#ffffff;font-size:26px;font-weight:bold;">Confirma tu correo</td></tr>
                        <tr><td style="padding:8px 32px 20px;color:#dfd8f0;font-size:15px;line-height:1.6;">
                          Hola %s, gracias por crear tu cuenta en FluxGuard. Da clic en el botón para activarla:
                        </td></tr>
                        <tr><td align="center" style="padding:0 32px 24px;">
                          <a href="%s" style="display:inline-block;padding:14px 26px;background:#b9a7ff;color:#0b0812;text-decoration:none;font-weight:bold;font-size:13px;letter-spacing:2px;">VERIFICAR MI CUENTA</a>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px;color:#9a90b5;font-size:12px;line-height:1.6;">
                          El enlace vence en %d horas. Si no creaste esta cuenta, puedes ignorar este correo.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(nombre), HtmlUtils.htmlEscape(enlace), horasVigencia);
    }
}
