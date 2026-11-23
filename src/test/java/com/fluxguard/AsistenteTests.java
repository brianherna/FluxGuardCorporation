package com.fluxguard;

import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fluxguard.controller.AsistenteController;
import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.AsistenteRequest;
import com.fluxguard.dto.AsistenteRequest.Mensaje;
import com.fluxguard.model.Suscripcion;
import com.fluxguard.model.Usuario;
import com.fluxguard.service.AsistenteService;
import com.fluxguard.service.CatalogoService;
import com.fluxguard.service.LimiteSolicitudes;
import com.fluxguard.service.SesionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsistenteTests {

    private final List<MessageCreateParams> solicitudes = new ArrayList<>();
    private BetaMessage siguiente;
    private SesionService sesionService;
    private Usuario usuario;
    private AsistenteController controlador;

    private static BetaMessage respuesta(String stopReason, String texto) throws Exception {
        return ObjectMappers.jsonMapper().readValue("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "stop_reason":"%s","stop_sequence":null,
                 "content":[{"type":"text","text":%s}],
                 "usage":{"input_tokens":10,"output_tokens":5}}""".formatted(stopReason,
                ObjectMappers.jsonMapper().writeValueAsString(texto)), BetaMessage.class);
    }

    private static AsistenteRequest conversacion(String... textos) {
        AsistenteRequest request = new AsistenteRequest();
        List<Mensaje> mensajes = new ArrayList<>();
        for (int i = 0; i < textos.length; i++) {
            mensajes.add(new Mensaje(i % 2 == 0 ? "usuario" : "asistente", textos[i]));
        }
        request.setMensajes(mensajes);
        return request;
    }

    @BeforeEach
    void setUp() throws Exception {
        siguiente = respuesta("end_turn", "Tu plan Standard incluye hasta 5 circuitos.");
        AsistenteService asistente = new AsistenteService(solicitud -> {
            solicitudes.add(solicitud);
            return siguiente;
        }, new CatalogoService());
        usuario = new Usuario("Ana", "Lopez", "ana@fluxguard.io", "hash");
        usuario.setId("u1");
        sesionService = mock(SesionService.class);
        when(sesionService.usuarioDesde("Bearer valido")).thenReturn(Optional.of(usuario));
        controlador = new AsistenteController(asistente, sesionService, new LimiteSolicitudes());
    }

    @Test
    void respondeConElTextoDeClaude() {
        ResponseEntity<ApiResponse<Void>> r = controlador.preguntar("Bearer valido", conversacion("¿Cuántos circuitos tengo?"));

        assertEquals(200, r.getStatusCode().value());
        assertEquals("Tu plan Standard incluye hasta 5 circuitos.", r.getBody().getRespuesta());
    }

    @Test
    void laSolicitudUsaElModeloElRespaldoYLaConversacionEnOrden() throws Exception {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setPlan("Standard");
        suscripcion.setCircuitos(5);
        usuario.setSuscripcion(suscripcion);

        controlador.preguntar("Bearer valido", conversacion("Hola", "Hola, ¿en qué te ayudo?", "¿Qué plan tengo?"));

        MessageCreateParams solicitud = solicitudes.get(0);
        String cuerpo = ObjectMappers.jsonMapper().writeValueAsString(solicitud._body());
        assertTrue(cuerpo.contains("\"model\":\"claude-opus-5-5\""), cuerpo);
        assertTrue(cuerpo.contains("\"fallbacks\":\"default\""), cuerpo);
        assertTrue(cuerpo.contains("\"effort\":\"low\""), cuerpo);
        assertTrue(solicitud._headers().values("anthropic-beta").toString().contains("server-side-fallback-2026-07-01"));
        assertEquals(3, solicitud.messages().size());
        assertTrue(cuerpo.indexOf("Hola, ¿en qué te ayudo?") < cuerpo.indexOf("¿Qué plan tengo?"));
        // El asistente sabe del catálogo y del plan del cliente, no de otros clientes
        assertTrue(cuerpo.contains("Kit Piloto FluxGuard: $350 MXN"), cuerpo);
        assertTrue(cuerpo.contains("Plan Standard con 5 circuitos"), cuerpo);
    }

    @Test
    void siClaudeSeNiegaSeRespondeUnMensajeFijo() throws Exception {
        siguiente = respuesta("refusal", "");

        ResponseEntity<ApiResponse<Void>> r = controlador.preguntar("Bearer valido", conversacion("algo"));

        assertEquals(200, r.getStatusCode().value());
        assertTrue(r.getBody().getRespuesta().startsWith("No puedo ayudarte con eso."));
    }

    @Test
    void siAnthropicFallaRespondeNoDisponible() {
        AsistenteService caido = new AsistenteService(solicitud -> {
            throw new AnthropicIoException("sin conexión");
        }, new CatalogoService());
        controlador = new AsistenteController(caido, sesionService, new LimiteSolicitudes());

        ResponseEntity<ApiResponse<Void>> r = controlador.preguntar("Bearer valido", conversacion("Hola"));

        assertEquals(503, r.getStatusCode().value());
        assertFalse(r.getBody().isSuccess());
    }

    @Test
    void sinSesionOSinLlaveNoSeLlamaAClaude() {
        assertEquals(401, controlador.preguntar(null, conversacion("Hola")).getStatusCode().value());

        AsistenteService sinLlave = new AsistenteService("", new CatalogoService());
        assertFalse(sinLlave.estaConfigurado());
        controlador = new AsistenteController(sinLlave, sesionService, new LimiteSolicitudes());
        assertEquals(503, controlador.preguntar("Bearer valido", conversacion("Hola")).getStatusCode().value());

        assertTrue(solicitudes.isEmpty());
    }

    @Test
    void conversacionesInvalidasSeRechazan() {
        String largo = "a".repeat(1001);
        for (AsistenteRequest invalida : List.of(
                new AsistenteRequest(),
                conversacion(),
                conversacion("Hola", "Respuesta"),          // termina con el asistente
                conversacion("   "),
                conversacion(largo),
                conversacion(new String[21]))) {
            assertEquals(400, controlador.preguntar("Bearer valido", invalida).getStatusCode().value());
        }
        AsistenteRequest desordenada = conversacion("Hola");
        desordenada.getMensajes().get(0).setRol("asistente");
        assertEquals(400, controlador.preguntar("Bearer valido", desordenada).getStatusCode().value());
        assertEquals(400, controlador.preguntar("Bearer valido", null).getStatusCode().value());

        assertTrue(solicitudes.isEmpty());
    }

    @Test
    void hayUnLimiteDePreguntasPorHora() {
        for (int i = 0; i < 30; i++) {
            assertEquals(200, controlador.preguntar("Bearer valido", conversacion("Pregunta " + i)).getStatusCode().value());
        }

        assertEquals(429, controlador.preguntar("Bearer valido", conversacion("Una más")).getStatusCode().value());
        assertEquals(30, solicitudes.size());
    }
}
