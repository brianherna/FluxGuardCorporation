package com.fluxguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fluxguard.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Cliente de la API de órdenes de PayPal (v2): https://developer.paypal.com/docs/api/orders/v2/
 * Por defecto usa PayPal Sandbox (pagos de prueba, sin dinero real). Lee PAYPAL_CLIENT_ID,
 * PAYPAL_CLIENT_SECRET, PAYPAL_MODE (sandbox | live) y PAYPAL_CURRENCY del .env o de variables de entorno.
 * Los datos de pago del cliente nunca pasan por este servidor: paga en la página de PayPal.
 */
@Service
public class PayPalService {

    private static final Logger log = LoggerFactory.getLogger(PayPalService.class);

    public record Orden(String id, String urlAprobacion) {}

    /**
     * Resultado de capturar (cobrar) una orden. status: COMPLETED, PENDING, DECLINED, FAILED.
     * nombrePagador y correoPagador vienen de la cuenta de PayPal con la que se pagó.
     */
    public record Captura(String id, String status, BigDecimal monto, String moneda,
                          String nombrePagador, String correoPagador) {
        public Captura(String id, String status, BigDecimal monto, String moneda) {
            this(id, status, monto, moneda, null, null);
        }
    }

    /** PayPal no respondió o rechazó la operación. */
    public static class PayPalException extends RuntimeException {
        public PayPalException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String moneda;
    private final boolean sandbox;
    private final String appBaseUrl;

    private String tokenAcceso;
    private Instant tokenVence = Instant.EPOCH;

    public PayPalService(
            @Value("${paypal.client-id:${PAYPAL_CLIENT_ID:}}") String clientId,
            @Value("${paypal.client-secret:${PAYPAL_CLIENT_SECRET:}}") String clientSecret,
            @Value("${paypal.mode:${PAYPAL_MODE:sandbox}}") String modo,
            @Value("${paypal.currency:${PAYPAL_CURRENCY:MXN}}") String moneda,
            @Value("${app.base-url:${APP_BASE_URL:http://localhost:8080}}") String appBaseUrl) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.moneda = moneda.trim().toUpperCase();
        this.sandbox = !"live".equalsIgnoreCase(modo.trim());
        this.appBaseUrl = appBaseUrl.trim().replaceAll("/+$", "");

        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(5_000);
        tiempos.setReadTimeout(20_000);
        this.restClient = RestClient.builder()
                .baseUrl(sandbox ? "https://api-m.sandbox.paypal.com" : "https://api-m.paypal.com")
                .requestFactory(tiempos)
                .build();

        if (!estaConfigurado()) {
            log.warn("[FluxG] PayPal no está configurado (PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET). No se podrán cobrar pedidos.");
        } else {
            log.info("[FluxG] PayPal en modo {} ({}).", sandbox ? "SANDBOX (pagos de prueba)" : "LIVE (pagos reales)", this.moneda);
        }
    }

    public boolean estaConfigurado() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    public boolean esSandbox() {
        return sandbox;
    }

    public String getMoneda() {
        return moneda;
    }

    /** Crea la orden de PayPal del pedido y devuelve la URL donde el cliente aprueba el pago. */
    public Orden crearOrden(Pedido pedido) {
        List<Map<String, Object>> items = pedido.getLineas().stream()
                .filter(linea -> linea.precioUnitario() > 0) // el Plan Free ($0) no se manda a PayPal
                .map(linea -> Map.<String, Object>of(
                        "name", recortar(linea.nombre(), 127),
                        "sku", linea.productoId(),
                        "quantity", String.valueOf(linea.cantidad()),
                        "unit_amount", importe(linea.precioUnitario()),
                        "category", "DIGITAL_GOODS"))
                .toList();

        String regreso = appBaseUrl + "/carrito.html?pedido=" + pedido.getFolio();
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "reference_id", pedido.getId(),
                        "custom_id", pedido.getId(),
                        "invoice_id", pedido.getFolio(),
                        "description", "Pedido FluxGuard " + pedido.getFolio(),
                        "items", items,
                        "amount", Map.of(
                                "currency_code", moneda,
                                "value", decimal(pedido.getTotal()),
                                "breakdown", Map.of("item_total", importe(pedido.getTotal()))))),
                "payment_source", Map.of("paypal", Map.of("experience_context", Map.of(
                        "brand_name", "FluxGuard",
                        "locale", "es-MX",
                        "shipping_preference", "NO_SHIPPING",
                        "user_action", "PAY_NOW",
                        "return_url", regreso,
                        "cancel_url", regreso + "&cancelado=1"))));

        try {
            JsonNode respuesta = restClient.post()
                    .uri("/v2/checkout/orders")
                    .header("Authorization", "Bearer " + token())
                    .header("PayPal-Request-Id", "orden-" + pedido.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String url = enlace(respuesta, "payer-action");
            if (url == null) url = enlace(respuesta, "approve");
            if (respuesta == null || url == null) {
                throw new PayPalException("PayPal no devolvió la URL de pago.", null);
            }
            return new Orden(respuesta.path("id").asText(), url);
        } catch (RestClientResponseException e) {
            log.error("[FluxG] PayPal rechazó la orden del pedido {} ({}): {}",
                    pedido.getFolio(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new PayPalException("PayPal rechazó la solicitud de pago.", e);
        } catch (RestClientException e) {
            log.error("[FluxG] No se pudo conectar con PayPal: {}", e.getMessage());
            throw new PayPalException("No se pudo conectar con PayPal.", e);
        }
    }

    /**
     * Cobra una orden ya aprobada por el cliente. Si ya se había cobrado (p. ej. el cliente recargó la página),
     * devuelve el cobro existente en lugar de fallar.
     */
    public Captura capturar(String ordenId) {
        try {
            JsonNode respuesta = restClient.post()
                    .uri("/v2/checkout/orders/{id}/capture", ordenId)
                    .header("Authorization", "Bearer " + token())
                    .header("PayPal-Request-Id", "captura-" + ordenId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(JsonNode.class);
            return leerCaptura(respuesta);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_ENTITY)
                    && e.getResponseBodyAsString().contains("ORDER_ALREADY_CAPTURED")) {
                return leerCaptura(obtenerOrden(ordenId));
            }
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_ENTITY)) {
                // Orden no aprobada, tarjeta rechazada (INSTRUMENT_DECLINED), etc.
                log.warn("[FluxG] PayPal no pudo cobrar la orden {}: {}", ordenId, e.getResponseBodyAsString());
                return new Captura(null, "DECLINED", null, null);
            }
            log.error("[FluxG] Error al cobrar la orden {} ({}): {}", ordenId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PayPalException("PayPal no pudo procesar el cobro.", e);
        } catch (RestClientException e) {
            log.error("[FluxG] No se pudo conectar con PayPal: {}", e.getMessage());
            throw new PayPalException("No se pudo conectar con PayPal.", e);
        }
    }

    private JsonNode obtenerOrden(String ordenId) {
        return restClient.get()
                .uri("/v2/checkout/orders/{id}", ordenId)
                .header("Authorization", "Bearer " + token())
                .retrieve()
                .body(JsonNode.class);
    }

    private static Captura leerCaptura(JsonNode orden) {
        JsonNode captura = orden == null ? null : orden.path("purchase_units").path(0).path("payments").path("captures").path(0);
        if (captura == null || captura.isMissingNode()) {
            return new Captura(null, orden == null ? "FAILED" : orden.path("status").asText("FAILED"), null, null);
        }
        JsonNode monto = captura.path("amount");
        JsonNode pagador = orden.path("payer");
        String nombre = (pagador.path("name").path("given_name").asText("") + " "
                + pagador.path("name").path("surname").asText("")).trim();
        return new Captura(
                captura.path("id").asText(null),
                captura.path("status").asText(),
                monto.hasNonNull("value") ? new BigDecimal(monto.get("value").asText()) : null,
                monto.path("currency_code").asText(null),
                nombre.isEmpty() ? null : nombre,
                pagador.path("email_address").asText(null));
    }

    /** Token OAuth de la aplicación; PayPal lo da por ~9 horas, se reutiliza hasta poco antes de que venza. */
    private synchronized String token() {
        if (tokenAcceso != null && Instant.now().isBefore(tokenVence)) {
            return tokenAcceso;
        }
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        try {
            JsonNode respuesta = restClient.post()
                    .uri("/v1/oauth2/token")
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(JsonNode.class);
            if (respuesta == null || !respuesta.hasNonNull("access_token")) {
                throw new PayPalException("PayPal no devolvió un token de acceso.", null);
            }
            tokenAcceso = respuesta.get("access_token").asText();
            tokenVence = Instant.now().plusSeconds(Math.max(60, respuesta.path("expires_in").asLong(3600) - 300));
            return tokenAcceso;
        } catch (RestClientResponseException e) {
            log.error("[FluxG] PayPal rechazó las credenciales ({}). Revisa PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET y PAYPAL_MODE.",
                    e.getStatusCode());
            throw new PayPalException("No se pudo autenticar con PayPal.", e);
        } catch (RestClientException e) {
            throw new PayPalException("No se pudo conectar con PayPal.", e);
        }
    }

    private Map<String, String> importe(int pesos) {
        return Map.of("currency_code", moneda, "value", decimal(pesos));
    }

    private static String decimal(int pesos) {
        return pesos + ".00";
    }

    private static String enlace(JsonNode orden, String rel) {
        if (orden == null) return null;
        for (JsonNode link : orden.path("links")) {
            if (rel.equals(link.path("rel").asText())) {
                return link.path("href").asText();
            }
        }
        return null;
    }

    private static String recortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
