package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.SuscripcionRequest;
import com.fluxguard.dto.UsuarioDto;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.SesionService;
import com.fluxguard.service.SuscripcionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Activa el plan elegido en pago.html y lo guarda en el usuario de la sesión.
 * El precio y los circuitos los decide el servidor, no el navegador.
 * Todavía no hay cobro real: falta integrar una pasarela de pago (Stripe, Mercado Pago, etc.).
 */
@RestController
@RequestMapping("/api/suscripcion")
public class SuscripcionController {

    private final UsuarioRepository usuarioRepository;
    private final SesionService sesionService;
    private final SuscripcionService suscripcionService;

    public SuscripcionController(UsuarioRepository usuarioRepository, SesionService sesionService,
                                 SuscripcionService suscripcionService) {
        this.usuarioRepository = usuarioRepository;
        this.sesionService = sesionService;
        this.suscripcionService = suscripcionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UsuarioDto>> activar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SuscripcionRequest request) {
        Optional<Usuario> optionalUsuario = sesionService.usuarioDesde(authorization);
        if (optionalUsuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Tu sesión expiró. Inicia sesión de nuevo."));
        }

        String tipo = request.getPlan() != null ? request.getPlan().trim().toLowerCase() : "";
        if (!suscripcionService.existe(tipo)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("El plan seleccionado no existe."));
        }

        Usuario usuario = optionalUsuario.get();
        suscripcionService.activar(usuario, tipo);
        Usuario guardado = usuarioRepository.save(usuario);

        ApiResponse<UsuarioDto> response = ApiResponse.ok("Suscripción activada");
        response.setUsuario(UsuarioDto.de(guardado));
        return ResponseEntity.ok(response);
    }
}
