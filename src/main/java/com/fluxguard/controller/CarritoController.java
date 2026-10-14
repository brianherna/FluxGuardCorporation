package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.CarritoDto;
import com.fluxguard.dto.CarritoItemRequest;
import com.fluxguard.service.CarritoService;
import com.fluxguard.service.CarritoService.CarritoException;
import com.fluxguard.service.CatalogoService;
import com.fluxguard.service.Comprador;
import com.fluxguard.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.function.Function;

/**
 * Catálogo público de la tienda y carrito. Funciona con sesión (Authorization) o como invitado
 * (encabezado X-Carrito-Invitado con un token aleatorio que genera el navegador).
 */
@RestController
@RequestMapping("/api")
public class CarritoController {

    static final String INVITADO = "X-Carrito-Invitado";

    private final CatalogoService catalogoService;
    private final CarritoService carritoService;
    private final SesionService sesionService;

    public CarritoController(CatalogoService catalogoService, CarritoService carritoService, SesionService sesionService) {
        this.catalogoService = catalogoService;
        this.carritoService = carritoService;
        this.sesionService = sesionService;
    }

    @GetMapping("/catalogo")
    public ResponseEntity<ApiResponse<Void>> catalogo() {
        ApiResponse<Void> response = ApiResponse.ok("Catálogo");
        response.setProductos(catalogoService.todos());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/carrito")
    public ResponseEntity<ApiResponse<Void>> ver(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @RequestHeader(value = INVITADO, required = false) String invitado) {
        return conCarrito(authorization, invitado, carritoService::ver);
    }

    @PostMapping("/carrito/items")
    public ResponseEntity<ApiResponse<Void>> agregar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     @RequestHeader(value = INVITADO, required = false) String invitado,
                                                     @RequestBody CarritoItemRequest request) {
        String productoId = request.getProductoId() != null ? request.getProductoId().trim() : "";
        int cantidad = request.getCantidad() != null ? request.getCantidad() : 1;
        return conCarrito(authorization, invitado, comprador -> carritoService.agregar(comprador, productoId, cantidad));
    }

    @PutMapping("/carrito/items/{productoId}")
    public ResponseEntity<ApiResponse<Void>> cambiar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     @RequestHeader(value = INVITADO, required = false) String invitado,
                                                     @PathVariable String productoId,
                                                     @RequestBody CarritoItemRequest request) {
        int cantidad = request.getCantidad() != null ? request.getCantidad() : 0;
        return conCarrito(authorization, invitado, comprador -> carritoService.cambiarCantidad(comprador, productoId, cantidad));
    }

    @DeleteMapping("/carrito/items/{productoId}")
    public ResponseEntity<ApiResponse<Void>> quitar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @RequestHeader(value = INVITADO, required = false) String invitado,
                                                    @PathVariable String productoId) {
        return conCarrito(authorization, invitado, comprador -> carritoService.cambiarCantidad(comprador, productoId, 0));
    }

    /** Al iniciar sesión, el navegador manda su token de invitado para pasar ese carrito a la cuenta. */
    @PostMapping("/carrito/fusionar")
    public ResponseEntity<ApiResponse<Void>> fusionar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @RequestHeader(value = INVITADO, required = false) String invitado) {
        Optional<Comprador> usuario = sesionService.compradorDesde(authorization, null);
        Optional<Comprador> carritoInvitado = sesionService.compradorDesde(null, invitado);
        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Inicia sesión para continuar."));
        }
        ApiResponse<Void> response = ApiResponse.ok("Carrito");
        response.setCarrito(carritoInvitado.isPresent()
                ? carritoService.fusionar(carritoInvitado.get(), usuario.get())
                : carritoService.ver(usuario.get()));
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<ApiResponse<Void>> conCarrito(String authorization, String invitado,
                                                         Function<Comprador, CarritoDto> accion) {
        Optional<Comprador> comprador = sesionService.compradorDesde(authorization, invitado);
        if (comprador.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Tu sesión expiró. Recarga la página."));
        }
        try {
            ApiResponse<Void> response = ApiResponse.ok("Carrito");
            response.setCarrito(accion.apply(comprador.get()));
            return ResponseEntity.ok(response);
        } catch (CarritoException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
