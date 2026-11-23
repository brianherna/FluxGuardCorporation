package com.fluxguard.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fluxguard.model.Producto;
import com.fluxguard.model.Suscripcion;
import com.fluxguard.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Asistente de la app móvil: responde dudas sobre FluxGuard con Claude (API de Anthropic).
 * Lee ANTHROPIC_API_KEY del .env o de variables de entorno; vacía = la app dice que el asistente no está disponible.
 */
@Service
public class AsistenteService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteService.class);
    public static final String MODELO = "claude-opus-5-5";
    // Si Claude rechaza una pregunta por sus filtros de seguridad, Anthropic la reintenta con otro modelo
    static final String BETA_RESPALDO = "server-side-fallback-2026-07-01";
    static final String RESPUESTA_RECHAZADA =
            "No puedo ayudarte con eso. Si tu duda es sobre tu equipo o tu cuenta, escríbenos por WhatsApp desde Soporte.";

    /** Un mensaje de la conversación, ya validado por el controlador. */
    public record Turno(boolean delUsuario, String texto) {}

    /** No se pudo obtener respuesta (Anthropic caído, límite de uso, llave inválida...). */
    public static class AsistenteException extends RuntimeException {
        public AsistenteException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    /** La llamada a Claude, separada para poder probar el servicio sin red. */
    @FunctionalInterface
    public interface Modelo {
        BetaMessage crear(MessageCreateParams solicitud);
    }

    private final Modelo modelo;
    private final String instrucciones;

    @Autowired
    public AsistenteService(@Value("${anthropic.api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
                            CatalogoService catalogo) {
        this(crearModelo(apiKey.trim()), catalogo);
    }

    public AsistenteService(Modelo modelo, CatalogoService catalogo) {
        this.modelo = modelo;
        this.instrucciones = instrucciones(catalogo.todos());
    }

    private static Modelo crearModelo(String apiKey) {
        if (apiKey.isEmpty()) {
            return null;
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(60))
                .build();
        log.info("[FluxG] Asistente de la app activo con {}.", MODELO);
        return solicitud -> client.beta().messages().create(solicitud);
    }

    public boolean estaConfigurado() {
        return modelo != null;
    }

    public String responder(Usuario usuario, List<Turno> conversacion) {
        BetaMessage respuesta;
        try {
            respuesta = modelo.crear(solicitud(usuario, conversacion));
        } catch (AnthropicException e) {
            log.error("[FluxG] El asistente no pudo responder: {}", e.getMessage());
            throw new AsistenteException("El asistente no está disponible en este momento. Intenta en unos minutos.", e);
        }
        return texto(respuesta);
    }

    MessageCreateParams solicitud(Usuario usuario, List<Turno> conversacion) {
        MessageCreateParams.Builder solicitud = MessageCreateParams.builder()
                .model(MODELO)
                .maxTokens(16000L)
                // Charla corta de soporte: con poco esfuerzo responde rápido y gasta menos
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                .addBeta(BETA_RESPALDO)
                .fallbacksDefault()
                .system(instrucciones + datosDe(usuario));
        for (Turno turno : conversacion) {
            if (turno.delUsuario()) {
                solicitud.addUserMessage(turno.texto());
            } else {
                solicitud.addAssistantMessage(turno.texto());
            }
        }
        return solicitud.build();
    }

    static String texto(BetaMessage respuesta) {
        if (respuesta.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
            return RESPUESTA_RECHAZADA;
        }
        String texto = respuesta.content().stream()
                .flatMap(bloque -> bloque.text().stream())
                .map(bloque -> bloque.text())
                .collect(Collectors.joining("\n"))
                .trim();
        return texto.isEmpty() ? RESPUESTA_RECHAZADA : texto;
    }

    private static String datosDe(Usuario usuario) {
        Suscripcion suscripcion = usuario.getSuscripcion();
        return "\n\nCliente con sesión iniciada: " + usuario.getNombre() + ". "
                + (suscripcion == null
                ? "No tiene plan activo."
                : "Plan " + suscripcion.getPlan() + " con " + suscripcion.getCircuitos() + " circuitos.");
    }

    private static String instrucciones(List<Producto> productos) {
        String catalogo = productos.stream()
                .map(p -> "- " + p.nombre() + ": $" + p.precio() + " MXN " + p.unidad().toLowerCase() + ". " + p.descripcion())
                .collect(Collectors.joining("\n"));
        return """
                Eres el asistente de FluxGuard dentro de su app móvil. FluxGuard (fluxguardcorporation.com) es una \
                empresa de Mixquiahuala de Juárez, Hidalgo, que hace equipos y planes para monitorear instalaciones \
                eléctricas: voltaje, corriente, potencia y frecuencia.

                La app es un complemento de la página web, no la sustituye. En la app el cliente ve su dashboard, \
                las reseñas, sus compras y sus notificaciones, y habla contigo. Comprar equipos, contratar o mejorar \
                un plan y pedir cotizaciones se hace en la página web: en la app, menú ☰ > "En la página web". \
                Por ahora las lecturas del dashboard de la app son de demostración, no las de su equipo; si preguntan \
                por sus datos reales, dilo con claridad.

                Catálogo (precios con IVA):
                %s
                - Plan Ultimate: más de 5 circuitos y varias sedes, a cotizar con ventas.

                Soporte humano: WhatsApp +52 771 436 8035 o fluxguardcorporation@gmail.com (en la app: menú ☰ > Soporte).

                Responde en español de México, en 2 a 5 oraciones y en texto plano, sin Markdown ni listas con \
                símbolos. No inventes precios, fechas, estados de pedidos, lecturas ni datos de la cuenta: solo \
                sabes lo que está aquí. Si la duda necesita revisar su instalación, un pago o un pedido, manda al \
                cliente con soporte humano. Si te preguntan algo ajeno a FluxGuard o a la energía eléctrica, dilo \
                amablemente y vuelve al tema.""".formatted(catalogo);
    }
}
