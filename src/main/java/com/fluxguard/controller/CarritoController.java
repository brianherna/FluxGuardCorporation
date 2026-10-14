package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.CarritoDto;
import com.fluxguard.dto.CarritoItemRequest;
import com.fluxguard.dto.UsuarioDto;
import com.fluxguard.model.Producto;
import com.fluxguard.model.Usuario;
import com.fluxguard.service.CarritoService;
import com.fluxguard.service.CarritoService.CarritoException;
import com.fluxguard.service.CatalogoService;
import com.fluxguard.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Catálogo público de la tienda y carrito del usuario de la sesión. */
@RestController
@RequestMapping("/api")
public class CarritoController {

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
    public ResponseEntity<ApiResponse<Void>> ver(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return conCarrito(authorization, carritoService::ver);
    }

    @PostMapping("/carrito/items")
    public ResponseEntity<ApiResponse<Void>> agregar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     @RequestBody CarritoItemRequest request) {
        String productoId = request.getProductoId() != null ? request.getProductoId().trim() : "";
        int cantidad = request.getCantidad() != null ? request.getCantidad() : 1;
        return conCarrito(authorization, usuario -> carritoService.agregar(usuario, productoId, cantidad));
    }

    @PutMapping("/carrito/items/{productoId}")
    public ResponseEntity<ApiResponse<Void>> cambiar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     @PathVariable String productoId,
                                                     @RequestBody CarritoItemRequest request) {
        int cantidad = request.getCantidad() != null ? request.getCantidad() : 0;
        return conCarrito(authorization, usuario -> carritoService.cambiarCantidad(usuario, productoId, cantidad));
    }

    @DeleteMapping("/carrito/items/{productoId}")
    public ResponseEntity<ApiResponse<Void>> quitar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @PathVariable String productoId) {
        return conCarrito(authorization, usuario -> carritoService.cambiarCantidad(usuario, productoId, 0));
    }

    @PostMapping("/carrito/comprar")
    public ResponseEntity<ApiResponse<UsuarioDto>> comprar(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<Usuario> usuario = sesionService.usuarioDesde(authorization);
        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Inicia sesión para continuar."));
        }
        try {
            CarritoService.Compra compra = carritoService.comprar(usuario.get());
            ApiResponse<UsuarioDto> response = ApiResponse.ok("¡Compra confirmada! Folio " + compra.pedido().getFolio() + ".");
            response.setPedido(compra.pedido());
            response.setUsuario(UsuarioDto.de(compra.usuario()));
            return ResponseEntity.ok(response);
        } catch (CarritoException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    private ResponseEntity<ApiResponse<Void>> conCarrito(String authorization, Function<Usuario, CarritoDto> accion) {
        Optional<Usuario> usuario = sesionService.usuarioDesde(authorization);
        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Inicia sesión para usar el carrito."));
        }
        try {
            ApiResponse<Void> response = ApiResponse.ok("Carrito");
            response.setCarrito(accion.apply(usuario.get()));
            return ResponseEntity.ok(response);
        } catch (CarritoException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
