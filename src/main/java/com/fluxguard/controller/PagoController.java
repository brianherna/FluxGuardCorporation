package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.UsuarioDto;
import com.fluxguard.model.Pedido;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.CarritoService.CarritoException;
import com.fluxguard.service.Comprador;
import com.fluxguard.service.PagoService;
import com.fluxguard.service.PagoService.PagoNoDisponibleException;
import com.fluxguard.service.PayPalService.PayPalException;
import com.fluxguard.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Pago del carrito con PayPal: iniciar el pago, confirmarlo al regresar de PayPal y cancelarlo.
 * Igual que el carrito, funciona con sesión o como invitado (encabezado X-Carrito-Invitado).
 */
@RestController
@RequestMapping("/api")
public class PagoController {

    private static final Pattern ORDEN_PAYPAL = Pattern.compile("^[A-Z0-9]{5,40}$");

    private final PagoService pagoService;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SesionService sesionService;

    public PagoController(PagoService pagoService, PedidoRepository pedidoRepository,
                          UsuarioRepository usuarioRepository, SesionService sesionService) {
        this.pagoService = pagoService;
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.sesionService = sesionService;
    }

    /** Crea el pedido con el carrito actual y devuelve la URL de PayPal a la que hay que redirigir. */
    @PostMapping("/carrito/comprar")
    public ResponseEntity<ApiResponse<UsuarioDto>> comprar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                          @RequestHeader(value = CarritoController.INVITADO, required = false) String invitado) {
        Optional<Comprador> comprador = sesionService.compradorDesde(authorization, invitado);
        if (comprador.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Tu sesión expiró. Recarga la página."));
        }
        try {
            PagoService.Inicio inicio = pagoService.iniciar(comprador.get());
            ApiResponse<UsuarioDto> response = ApiResponse.ok(inicio.urlPago() != null
                    ? "Te estamos llevando a PayPal para completar el pago."
                    : "¡Listo! Tu pedido " + inicio.pedido().getFolio() + " quedó confirmado.");
            response.setPedido(inicio.pedido());
            response.setUrlPago(inicio.urlPago());
            if (inicio.urlPago() == null) {
                usuarioRepository.findById(comprador.get().clave()).map(UsuarioDto::de).ifPresent(response::setUsuario);
            }
            return ResponseEntity.ok(response);
        } catch (CarritoException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (PagoNoDisponibleException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(e.getMessage()));
        } catch (PayPalException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("No pudimos conectar con PayPal. Intenta de nuevo en unos momentos."));
        }
    }

    /** Estado de un pedido del usuario. */
    @GetMapping("/pedidos/{folio}")
    public ResponseEntity<ApiResponse<UsuarioDto>> pedido(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                         @RequestHeader(value = CarritoController.INVITADO, required = false) String invitado,
                                                         @PathVariable String folio) {
        return conPedido(authorization, invitado, folio, pedido -> pedido);
    }

    /** Al regresar de PayPal, carrito.html manda el "token" (id de la orden) y aquí se cobra. */
    @PostMapping("/pedidos/{folio}/confirmar")
    public ResponseEntity<ApiResponse<UsuarioDto>> confirmar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @RequestHeader(value = CarritoController.INVITADO, required = false) String invitado,
                                                            @PathVariable String folio,
                                                            @RequestParam("token") String ordenId) {
        if (!ORDEN_PAYPAL.matcher(ordenId).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("El pago no es válido."));
        }
        return conPedido(authorization, invitado, folio, pedido -> pagoService.confirmar(pedido, ordenId));
    }

    /** El cliente canceló en PayPal. */
    @PostMapping("/pedidos/{folio}/cancelar")
    public ResponseEntity<ApiResponse<UsuarioDto>> cancelar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                           @RequestHeader(value = CarritoController.INVITADO, required = false) String invitado,
                                                           @PathVariable String folio) {
        return conPedido(authorization, invitado, folio, pagoService::cancelar);
    }

    /** Busca el pedido del comprador (usuario o invitado), aplica la acción y responde con el pedido y el usuario actualizados. */
    private ResponseEntity<ApiResponse<UsuarioDto>> conPedido(String authorization, String invitado, String folio,
                                                              Function<Pedido, Pedido> accion) {
        Optional<Comprador> comprador = sesionService.compradorDesde(authorization, invitado);
        if (comprador.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Tu sesión expiró. Recarga la página."));
        }
        Optional<Pedido> pedido = pedidoRepository.findByFolio(folio)
                .filter(p -> p.getUsuarioId().equals(comprador.get().clave()));
        if (pedido.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("No encontramos ese pedido."));
        }
        try {
            Pedido actual = accion.apply(pedido.get());
            ApiResponse<UsuarioDto> response = ApiResponse.ok("Pedido " + actual.getFolio());
            response.setPedido(actual);
            if (!comprador.get().esInvitado()) {
                usuarioRepository.findById(comprador.get().clave()).map(UsuarioDto::de).ifPresent(response::setUsuario);
            }
            return ResponseEntity.ok(response);
        } catch (PayPalException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("No pudimos confirmar el pago con PayPal. Recarga la página en unos momentos."));
        }
    }
}
