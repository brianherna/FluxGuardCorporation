package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.SuscripcionRequest;
import com.fluxguard.dto.UsuarioDto;
import com.fluxguard.model.Suscripcion;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Activa el plan elegido en pago.html y lo guarda en el usuario de la sesión.
 * El precio y los circuitos los decide el servidor, no el navegador.
 * Todavía no hay cobro real: falta integrar una pasarela de pago (Stripe, Mercado Pago, etc.).
 */
@RestController
@RequestMapping("/api/suscripcion")
public class SuscripcionController {

    private record Plan(String nombre, String precio, int circuitos) {}

    private static final Map<String, Plan> PLANES = Map.of(
            "free", new Plan("Plan Free", "0", 1),
            "standard", new Plan("Plan Standard", "499", 5),
            "ultimate", new Plan("Plan Ultimate", "1,499", 12),
            "prototipo", new Plan("Prototipo Piloto", "350", 5)
    );

    private final UsuarioRepository usuarioRepository;
    private final SesionService sesionService;
    private final SecureRandom random = new SecureRandom();

    public SuscripcionController(UsuarioRepository usuarioRepository, SesionService sesionService) {
        this.usuarioRepository = usuarioRepository;
        this.sesionService = sesionService;
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
        Plan plan = PLANES.get(tipo);
        if (plan == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("El plan seleccionado no existe."));
        }

        LocalDate hoy = LocalDate.now();
        Usuario usuario = optionalUsuario.get();
        usuario.setSuscripcion(new Suscripcion(
                plan.nombre(), tipo, plan.precio(), "Activa", plan.circuitos(),
                hoy, hoy.plusMonths(1), "FLX-" + (100000 + random.nextInt(900000))
        ));
        Usuario guardado = usuarioRepository.save(usuario);

        ApiResponse<UsuarioDto> response = ApiResponse.ok("Suscripción activada");
        response.setUsuario(UsuarioDto.de(guardado));
        return ResponseEntity.ok(response);
    }
}
