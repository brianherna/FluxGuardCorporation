package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.AsistenteRequest;
import com.fluxguard.model.Usuario;
import com.fluxguard.service.AsistenteService;
import com.fluxguard.service.AsistenteService.AsistenteException;
import com.fluxguard.service.AsistenteService.Turno;
import com.fluxguard.service.LimiteSolicitudes;
import com.fluxguard.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Chat del asistente de la app móvil. Solo con sesión iniciada y con un límite por hora, porque cada
 * respuesta tiene costo en la API de Anthropic. La app manda la conversación reciente en cada pregunta;
 * aquí no se guarda nada.
 */
@RestController
@RequestMapping("/api/asistente")
public class AsistenteController {

    static final int MAX_MENSAJES = 20;
    static final int MAX_TEXTO = 1000;
    static final int MAX_PREGUNTAS_POR_HORA = 30;

    private final AsistenteService asistente;
    private final SesionService sesionService;
    private final LimiteSolicitudes limite;

    public AsistenteController(AsistenteService asistente, SesionService sesionService, LimiteSolicitudes limite) {
        this.asistente = asistente;
        this.sesionService = sesionService;
        this.limite = limite;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> preguntar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @RequestBody(required = false) AsistenteRequest request) {
        Optional<Usuario> usuario = sesionService.usuarioDesde(authorization);
        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Inicia sesión para usar el asistente."));
        }
        if (!asistente.estaConfigurado()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("El asistente todavía no está disponible. Escríbenos por WhatsApp desde Soporte."));
        }
        Optional<List<Turno>> conversacion = validar(request);
        if (conversacion.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("No pudimos leer tu pregunta. Intenta de nuevo."));
        }
        if (!limite.permitir("asistente:" + usuario.get().getId(), MAX_PREGUNTAS_POR_HORA, Duration.ofHours(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Hiciste muchas preguntas seguidas. Intenta de nuevo en un rato."));
        }
        try {
            ApiResponse<Void> response = ApiResponse.ok("Respuesta del asistente");
            response.setRespuesta(asistente.responder(usuario.get(), conversacion.get()));
            return ResponseEntity.ok(response);
        } catch (AsistenteException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(e.getMessage()));
        }
    }

    /** La conversación alterna usuario y asistente, empieza y termina con el usuario, y tiene tamaños razonables. */
    static Optional<List<Turno>> validar(AsistenteRequest request) {
        if (request == null || request.getMensajes() == null
                || request.getMensajes().isEmpty() || request.getMensajes().size() > MAX_MENSAJES) {
            return Optional.empty();
        }
        List<Turno> turnos = new ArrayList<>();
        for (AsistenteRequest.Mensaje mensaje : request.getMensajes()) {
            if (mensaje == null || mensaje.getTexto() == null) {
                return Optional.empty();
            }
            String texto = mensaje.getTexto().trim();
            boolean delUsuario = turnos.size() % 2 == 0;
            if (texto.isEmpty() || texto.length() > MAX_TEXTO
                    || !(delUsuario ? "usuario" : "asistente").equals(mensaje.getRol())) {
                return Optional.empty();
            }
            turnos.add(new Turno(delUsuario, texto));
        }
        return turnos.get(turnos.size() - 1).delUsuario() ? Optional.of(turnos) : Optional.empty();
    }
}
