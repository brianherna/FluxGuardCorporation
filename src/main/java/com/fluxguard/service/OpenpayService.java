package com.fluxguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fluxguard.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Pago en tienda con Paynet a través de Openpay (cargo con method "store"):
 * https://documents.openpay.mx/docs/store-charge.html
 * Openpay devuelve una referencia, la imagen del código de barras y una ficha en PDF; el cliente paga en
 * efectivo en tiendas participantes (7-Eleven, Walmart, farmacias...). Por defecto usa el sandbox (pruebas).
 * Lee OPENPAY_MERCHANT_ID, OPENPAY_PRIVATE_KEY y OPENPAY_PRODUCTION (false) del .env o de variables de entorno.
 */
@Service
public class OpenpayService {

    private static final Logger log = LoggerFactory.getLogger(OpenpayService.class);
    private static final int HORAS_VIGENCIA = 72;
    private static final DateTimeFormatter FECHA_OPENPAY = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Ficha de pago generada por Paynet. */
    public record Ficha(String cargoId, String referencia, String codigoBarrasUrl, String urlPdf, LocalDateTime vence) {}

    /** Openpay no respondió o rechazó la operación. */
    public static class OpenpayException extends RuntimeException {
        public OpenpayException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private final RestClient restClient;
    private final String merchantId;
    private final String privateKey;
    private final boolean produccion;

    public OpenpayService(
            @Value("${openpay.merchant-id:${OPENPAY_MERCHANT_ID:}}") String merchantId,
            @Value("${openpay.private-key:${OPENPAY_PRIVATE_KEY:}}") String privateKey,
            @Value("${openpay.production:${OPENPAY_PRODUCTION:false}}") boolean produccion) {
        this.merchantId = merchantId.trim();
        this.privateKey = privateKey.trim();
        this.produccion = produccion;

        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(5_000);
        tiempos.setReadTimeout(20_000);
        String basic = Base64.getEncoder().encodeToString((this.privateKey + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl((produccion ? "https://api.openpay.mx/v1/" : "https://sandbox-api.openpay.mx/v1/") + this.merchantId)
                .requestFactory(tiempos)
                .defaultHeader("Authorization", "Basic " + basic)
                .build();

        if (estaConfigurado()) {
            log.info("[FluxG] Paynet (Openpay) en modo {}.", produccion ? "PRODUCCIÓN" : "SANDBOX (fichas de prueba)");
        }
    }

    public boolean estaConfigurado() {
        return !merchantId.isEmpty() && !privateKey.isEmpty();
    }

    public boolean esSandbox() {
        return !produccion;
    }

    /**
     * Crea el cargo en tienda del pedido y devuelve la ficha (referencia, código de barras y PDF).
     * El folio del pedido se usa como order_id, así Openpay no crea dos fichas para el mismo pedido.
     */
    public Ficha crearFicha(Pedido pedido, String nombre, String apellido, String correo) {
        LocalDateTime vence = LocalDateTime.now().plusHours(HORAS_VIGENCIA);
        Map<String, Object> cliente = new HashMap<>();
        cliente.put("name", nombre);
        cliente.put("last_name", apellido == null || apellido.isBlank() ? "-" : apellido);
        cliente.put("email", correo);

        Map<String, Object> body = new HashMap<>();
        body.put("method", "store");
        body.put("amount", BigDecimal.valueOf(pedido.getTotal()).setScale(2));
        body.put("description", "Pedido FluxGuard " + pedido.getFolio());
        body.put("order_id", pedido.getFolio());
        body.put("due_date", vence.format(FECHA_OPENPAY));
        body.put("customer", cliente);

        try {
            JsonNode r = restClient.post()
                    .uri("/charges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode metodo = r == null ? null : r.path("payment_method");
            if (metodo == null || !metodo.hasNonNull("reference")) {
                throw new OpenpayException("Openpay no devolvió la referencia de pago.", null);
            }
            String referencia = metodo.get("reference").asText();
            String pdf = (produccion ? "https://dashboard.openpay.mx" : "https://sandbox-dashboard.openpay.mx")
                    + "/paynet-pdf/" + merchantId + "/" + referencia;
            return new Ficha(r.path("id").asText(), referencia, metodo.path("barcode_url").asText(null), pdf, vence);
        } catch (RestClientResponseException e) {
            log.error("[FluxG] Openpay rechazó la ficha del pedido {} ({}): {}",
                    pedido.getFolio(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new OpenpayException("Openpay rechazó la solicitud de la ficha.", e);
        } catch (RestClientException e) {
            log.error("[FluxG] No se pudo conectar con Openpay: {}", e.getMessage());
            throw new OpenpayException("No se pudo conectar con Openpay.", e);
        }
    }
}
