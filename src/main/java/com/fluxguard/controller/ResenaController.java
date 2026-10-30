package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.ResenaDto;
import com.fluxguard.dto.ResenaRequest;
import com.fluxguard.model.Usuario;
import com.fluxguard.service.ResenaService;
import com.fluxguard.service.ResenaService.ResenaException;
import com.fluxguard.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Reseñas post-compra: opinar requiere sesión y un pedido pagado.
 * Sin sesión solo se publican las positivas (/destacadas); con sesión se ven todas, buenas y malas.
 * ?seccion=planes devuelve las de los planes (para planes.html); sin él, las de la tienda.
 */
@RestController
@RequestMapping("/api/resenas")
public class ResenaController {

    private static final String REQUIERE_SESION = "Inicia sesión con tu cuenta para opinar sobre tu compra.";

    private final ResenaService resenaService;
    private final SesionService sesionService;

    public ResenaController(ResenaService resenaService, SesionService sesionService) {
        this.resenaService = resenaService;
        this.sesionService = sesionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> crear(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @RequestBody ResenaRequest request) {
        Optional<Usuario> usuario = sesionService.usuarioDesde(authorization);
        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(REQUIERE_SESION));
        }
        try {
            ResenaDto resena = ResenaDto.de(resenaService.crear(usuario.get(), request.getFolio(), request.getProductoId(),
                    request.getCalificacion(), request.getComentario()));
            ApiResponse<Void> response = ApiResponse.ok("¡Gracias por tu opinión!");
            response.setResena(resena);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ResenaException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    /** Reseñas que ya escribió el usuario, para no volver a pedirle que califique esos productos. */
    @GetMapping("/mias")
    public ResponseEntity<ApiResponse<Void>> mias(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<Usuario> usuario = sesionService.usuarioDesde(authorization);
        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(REQUIERE_SESION));
        }
        ApiResponse<Void> response = ApiResponse.ok("Tus reseñas");
        response.setResenas(resenaService.delUsuario(usuario.get()).stream().map(ResenaDto::de).toList());
        return ResponseEntity.ok(response);
    }

    /** Todas las reseñas (buenas y malas) para quien inició sesión. Sin sesión no se entregan las negativas. */
    @GetMapping
    public ResponseEntity<ApiResponse<Void>> todas(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @RequestParam(value = "seccion", defaultValue = "tienda") String seccion) {
        if (sesionService.usuarioDesde(authorization).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Inicia sesión para ver todas las opiniones de los clientes."));
        }
        ApiResponse<Void> response = ApiResponse.ok("Opiniones de clientes");
        response.setResenas(resenaService.recientes(ResenaService.Seccion.de(seccion)).stream().map(ResenaDto::de).toList());
        return ResponseEntity.ok(response);
    }

    /** Reseñas positivas para el carrusel que ven los visitantes sin sesión. */
    @GetMapping("/destacadas")
    public ResponseEntity<ApiResponse<Void>> destacadas(@RequestParam(value = "seccion", defaultValue = "tienda") String seccion) {
        ApiResponse<Void> response = ApiResponse.ok("Reseñas destacadas");
        response.setResenas(resenaService.destacadas(ResenaService.Seccion.de(seccion)).stream().map(ResenaDto::de).toList());
        return ResponseEntity.ok(response);
    }
}
