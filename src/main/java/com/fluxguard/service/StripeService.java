package com.fluxguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fluxguard.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Cliente de Stripe Checkout (pago con tarjeta en la página de Stripe): https://docs.stripe.com/api/checkout/sessions
 * Con una llave sk_test_... todo es modo de prueba (tarjeta 4242 4242 4242 4242, sin dinero real).
 * Lee STRIPE_SECRET_KEY y STRIPE_WEBHOOK_SECRET del .env o de variables de entorno.
 * Los datos de la tarjeta nunca pasan por este servidor.
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);
    private static final long TOLERANCIA_FIRMA_SEGUNDOS = 300;

    public record Sesion(String id, String url) {}

    /**
     * Estado de una sesión de pago. pagado = payment_status "paid".
     * montoCentavos y moneda ("mxn") se comparan contra el pedido antes de darlo por pagado.
     */
    public record SesionPago(String id, String referencia, boolean pagado, String estado,
                             long montoCentavos, String moneda, String pagoId, String nombre, String correo) {}

    /** Stripe no respondió o rechazó la operación. */
    public static class StripeException extends RuntimeException {
        public StripeException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private final RestClient restClient;
    private final String secretKey;
    private final String webhookSecret;
    private final String appBaseUrl;

    public StripeService(
            @Value("${stripe.secret-key:${STRIPE_SECRET_KEY:}}") String secretKey,
            @Value("${stripe.webhook-secret:${STRIPE_WEBHOOK_SECRET:}}") String webhookSecret,
            @Value("${app.base-url:${APP_BASE_URL:http://localhost:8080}}") String appBaseUrl) {
        this.secretKey = secretKey.trim();
        this.webhookSecret = webhookSecret.trim();
        this.appBaseUrl = appBaseUrl.trim().replaceAll("/+$", "");

        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(5_000);
        tiempos.setReadTimeout(20_000);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.stripe.com")
                .requestFactory(tiempos)
                .defaultHeader("Authorization", "Bearer " + this.secretKey)
                .build();

        if (estaConfigurado()) {
            log.info("[FluxG] Stripe en modo {}.", esPrueba() ? "PRUEBA (tarjetas ficticias)" : "LIVE (cobros reales)");
            if (this.webhookSecret.isEmpty()) {
                log.warn("[FluxG] STRIPE_WEBHOOK_SECRET vacío: los pagos con tarjeta se confirman solo cuando el cliente regresa a la página.");
            }
        }
    }

    public boolean estaConfigurado() {
        return !secretKey.isEmpty();
    }

    public boolean esPrueba() {
        return secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_");
    }

    /** Crea la sesión de Stripe Checkout del pedido y devuelve la URL de pago. */
    public Sesion crearSesion(Pedido pedido) {
        String regreso = appBaseUrl + "/carrito.html?pedido=" + pedido.getFolio();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("locale", "es-419");
        // Los métodos de pago (tarjeta, OXXO...) se activan en el Dashboard de Stripe > Settings > Payment methods;
        // la API ya no acepta payment_method_types al crear la sesión.
        // {CHECKOUT_SESSION_ID} lo reemplaza Stripe; carrito.html lo recibe como "token", igual que con PayPal
        form.add("success_url", regreso + "&token={CHECKOUT_SESSION_ID}");
        form.add("cancel_url", regreso + "&cancelado=1");
        form.add("client_reference_id", pedido.getId());
        form.add("metadata[folio]", pedido.getFolio());
        form.add("payment_intent_data[description]", "Pedido FluxGuard " + pedido.getFolio());
        if (pedido.getCorreoCliente() != null) {
            form.add("customer_email", pedido.getCorreoCliente());
        }

        int i = 0;
        for (Pedido.Linea linea : pedido.getLineas()) {
            if (linea.precioUnitario() <= 0) continue; // el Plan Free ($0) no se cobra
            String p = "line_items[" + i++ + "]";
            form.add(p + "[quantity]", String.valueOf(linea.cantidad()));
            form.add(p + "[price_data][currency]", "mxn");
            form.add(p + "[price_data][unit_amount]", String.valueOf(linea.precioUnitario() * 100L));
            form.add(p + "[price_data][product_data][name]", linea.nombre());
        }

        try {
            JsonNode respuesta = restClient.post()
                    .uri("/v1/checkout/sessions")
                    .header("Idempotency-Key", "sesion-" + pedido.getId())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (respuesta == null || !respuesta.hasNonNull("url")) {
                throw new StripeException("Stripe no devolvió la URL de pago.", null);
            }
            return new Sesion(respuesta.get("id").asText(), respuesta.get("url").asText());
        } catch (RestClientResponseException e) {
            log.error("[FluxG] Stripe rechazó la sesión del pedido {} ({}): {}",
                    pedido.getFolio(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new StripeException("Stripe rechazó la solicitud de pago.", e);
        } catch (RestClientException e) {
            log.error("[FluxG] No se pudo conectar con Stripe: {}", e.getMessage());
            throw new StripeException("No se pudo conectar con Stripe.", e);
        }
    }

    /** Consulta una sesión directamente en Stripe (la fuente confiable de si se pagó). */
    public SesionPago obtenerSesion(String sesionId) {
        try {
            JsonNode s = restClient.get()
                    .uri("/v1/checkout/sessions/{id}", sesionId)
                    .retrieve()
                    .body(JsonNode.class);
            if (s == null) {
                throw new StripeException("Stripe no devolvió la sesión.", null);
            }
            JsonNode cliente = s.path("customer_details");
            return new SesionPago(
                    s.path("id").asText(),
                    s.path("client_reference_id").asText(null),
                    "paid".equals(s.path("payment_status").asText()),
                    s.path("status").asText(),
                    s.path("amount_total").asLong(-1),
                    s.path("currency").asText(""),
                    s.path("payment_intent").asText(null),
                    cliente.path("name").asText(null),
                    cliente.path("email").asText(null));
        } catch (RestClientResponseException e) {
            log.error("[FluxG] Stripe no encontró la sesión {} ({}): {}", sesionId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new StripeException("No se encontró el pago en Stripe.", e);
        } catch (RestClientException e) {
            log.error("[FluxG] No se pudo consultar la sesión {}: {}", sesionId, e.getMessage());
            throw new StripeException("No se pudo conectar con Stripe.", e);
        }
    }

    public boolean webhookConfigurado() {
        return !webhookSecret.isEmpty();
    }

    /**
     * Verifica el encabezado Stripe-Signature de un webhook: https://docs.stripe.com/webhooks#verify-manually
     * Firma = HMAC-SHA256(secreto, "t.cuerpo"), y el evento no debe tener más de 5 minutos.
     */
    public boolean firmaValida(String encabezado, String cuerpo, long ahoraSegundos) {
        if (webhookSecret.isEmpty() || encabezado == null || cuerpo == null) {
            return false;
        }
        long t = -1;
        java.util.List<String> firmas = new java.util.ArrayList<>();
        for (String parte : encabezado.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) {
                try {
                    t = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (kv[0].equals("v1")) {
                firmas.add(kv[1]);
            }
        }
        if (t < 0 || firmas.isEmpty() || Math.abs(ahoraSegundos - t) > TOLERANCIA_FIRMA_SEGUNDOS) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] esperado = HexFormat.of().formatHex(
                    mac.doFinal((t + "." + cuerpo).getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.UTF_8);
            for (String firma : firmas) {
                if (MessageDigest.isEqual(esperado, firma.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("[FluxG] No se pudo verificar la firma de Stripe: {}", e.getMessage());
            return false;
        }
    }
}
