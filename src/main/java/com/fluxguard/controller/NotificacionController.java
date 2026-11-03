package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.DispositivoRequest;
import com.fluxguard.model.Usuario;
import com.fluxguard.service.NotificacionService;
import com.fluxguard.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Avisos de la cuenta y registro de los celulares que reciben push. Todo requiere sesión. */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private static final Set<String> PLATAFORMAS = Set.of("android", "ios");
    private static final int LARGO_MAXIMO_TOKEN = 4096;

    private final NotificacionService notificacionService;
    private final SesionService sesionService;

    public NotificacionController(NotificacionService notificacionService, SesionService sesionService) {
        this.notificacionService = notificacionService;
        this.sesionService = sesionService;
    }

    /** Los 50 avisos más recientes y cuántos faltan por leer. */
    @GetMapping
    public ResponseEntity<ApiResponse<Void>> listar(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<Usuario> usuario = sesionService.usuarioDesde(authorization);
        if (usuario.isEmpty()) {
            return sesionExpirada();
        }
        ApiResponse<Void> response = ApiResponse.ok("Notificaciones");
        response.setNotificaciones(notificacionService.recientes(usuario.get().getId()));
        response.setNoLeidas(notificacionService.noLeidas(usuario.get().getId()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/leidas")
    public ResponseEntity<ApiResponse<Void>> marcarLeidas(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return conUsuario(authorization, usuario -> notificacionService.marcarLeidas(usuario.getId()), "Notificaciones leídas");
    }

    @PostMapping("/dispositivos")
    public ResponseEntity<ApiResponse<Void>> registrar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @RequestBody DispositivoRequest request) {
        String plataforma = request.getPlataforma() != null ? request.getPlataforma().trim().toLowerCase() : "";
        if (!tokenValido(request.getToken()) || !PLATAFORMAS.contains(plataforma)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("El dispositivo no es válido."));
        }
        return conUsuario(authorization, usuario -> notificacionService.registrarDispositivo(usuario.getId(), request.getToken(), plataforma), "Dispositivo registrado");
    }

    /** La app lo llama antes de cerrar sesión, para que ese celular deje de recibir los avisos de la cuenta. */
    @PostMapping("/dispositivos/quitar")
    public ResponseEntity<ApiResponse<Void>> quitar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @RequestBody DispositivoRequest request) {
        if (!tokenValido(request.getToken())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("El dispositivo no es válido."));
        }
        return conUsuario(authorization, usuario -> notificacionService.quitarDispositivo(usuario.getId(), request.getToken()), "Dispositivo quitado");
    }

    private static boolean tokenValido(String token) {
        return token != null && !token.isBlank() && token.length() <= LARGO_MAXIMO_TOKEN;
    }

    private ResponseEntity<ApiResponse<Void>> conUsuario(String authorization, Consumer<Usuario> accion, String mensaje) {
        Optional<Usuario> usuario = sesionService.usuarioDesde(authorization);
        if (usuario.isEmpty()) {
            return sesionExpirada();
        }
        accion.accept(usuario.get());
        return ResponseEntity.ok(ApiResponse.ok(mensaje));
    }

    private static ResponseEntity<ApiResponse<Void>> sesionExpirada() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Tu sesión expiró. Inicia sesión de nuevo."));
    }
}
