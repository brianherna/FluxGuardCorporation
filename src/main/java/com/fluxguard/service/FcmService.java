package com.fluxguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Push a los celulares con Firebase Cloud Messaging: https://firebase.google.com/docs/cloud-messaging/send-message
 * Lee FIREBASE_SERVICE_ACCOUNT_JSON (la cuenta de servicio del proyecto, en una sola línea) del .env o de variables
 * de entorno. Vacía = no se envían push; los avisos siguen disponibles en /api/notificaciones.
 */
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);
    private static final String URL_TOKEN = "https://oauth2.googleapis.com/token";
    private static final String ALCANCE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final long VIGENCIA_JWT_SEGUNDOS = 3600;

    /** Firebase no respondió o rechazó la operación. */
    public static class FcmException extends RuntimeException {
        public FcmException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private final RestClient restClient;
    private final ObjectMapper json = new ObjectMapper();
    private String proyecto;
    private String correoCuenta;
    private PrivateKey llave;
    private String tokenAcceso;
    private long tokenAccesoVence;

    public FcmService(@Value("${firebase.service-account:${FIREBASE_SERVICE_ACCOUNT_JSON:}}") String cuentaServicio) {
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(5_000);
        tiempos.setReadTimeout(10_000);
        this.restClient = RestClient.builder().requestFactory(tiempos).build();

        if (cuentaServicio.isBlank()) {
            return;
        }
        try {
            JsonNode cuenta = json.readTree(cuentaServicio);
            this.proyecto = cuenta.path("project_id").asText();
            this.correoCuenta = cuenta.path("client_email").asText();
            this.llave = leerLlave(cuenta.path("private_key").asText());
            log.info("[FluxG] Push con Firebase activo (proyecto {}).", proyecto);
        } catch (Exception e) {
            this.llave = null;
            log.error("[FluxG] FIREBASE_SERVICE_ACCOUNT_JSON no es válido: no se enviarán push.");
        }
    }

    public boolean estaConfigurado() {
        return llave != null;
    }

    /**
     * Envía el push a un celular.
     *
     * @return false si Firebase dice que ese token ya no existe (app desinstalada): hay que olvidarlo
     */
    public boolean enviar(String tokenDispositivo, String titulo, String cuerpo, Map<String, String> datos) {
        Map<String, Object> cuerpoPeticion = Map.of("message", Map.of(
                "token", tokenDispositivo,
                "notification", Map.of("title", titulo, "body", cuerpo),
                "data", datos,
                "android", Map.of("priority", "HIGH")));
        try {
            restClient.post()
                    .uri("https://fcm.googleapis.com/v1/projects/" + proyecto + "/messages:send")
                    .header("Authorization", "Bearer " + tokenAcceso())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpoPeticion)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return false;
            }
            throw new FcmException("Firebase rechazó el push: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new FcmException("No se pudo conectar con Firebase", e);
        }
    }

    /** Token de acceso de Google, reutilizado hasta un minuto antes de que venza. */
    private synchronized String tokenAcceso() {
        long ahora = Instant.now().getEpochSecond();
        if (tokenAcceso != null && ahora < tokenAccesoVence - 60) {
            return tokenAcceso;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", jwt(correoCuenta, llave, ahora));
        JsonNode respuesta = restClient.post()
                .uri(URL_TOKEN)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (respuesta == null || !respuesta.hasNonNull("access_token")) {
            throw new FcmException("Google no entregó el token de acceso", null);
        }
        tokenAcceso = respuesta.path("access_token").asText();
        tokenAccesoVence = ahora + respuesta.path("expires_in").asLong(VIGENCIA_JWT_SEGUNDOS);
        return tokenAcceso;
    }

    /** JWT firmado con la llave de la cuenta de servicio, que se cambia por el token de acceso. */
    public static String jwt(String correoCuenta, PrivateKey llave, long ahora) {
        String encabezado = base64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String datos = base64("{\"iss\":\"" + correoCuenta + "\",\"scope\":\"" + ALCANCE + "\",\"aud\":\"" + URL_TOKEN
                + "\",\"iat\":" + ahora + ",\"exp\":" + (ahora + VIGENCIA_JWT_SEGUNDOS) + "}");
        String contenido = encabezado + "." + datos;
        try {
            Signature firma = Signature.getInstance("SHA256withRSA");
            firma.initSign(llave);
            firma.update(contenido.getBytes(StandardCharsets.UTF_8));
            return contenido + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(firma.sign());
        } catch (GeneralSecurityException e) {
            throw new FcmException("No se pudo firmar el token de Firebase", e);
        }
    }

    private static String base64(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static PrivateKey leerLlave(String pem) throws GeneralSecurityException {
        String contenido = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(contenido)));
    }
}
