package com.fluxguard.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.UsuarioDto;
import com.fluxguard.model.Pedido;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.CarritoService.CarritoException;
import com.fluxguard.service.Comprador;
import com.fluxguard.service.PagoService;
import com.fluxguard.service.OpenpayService.OpenpayException;
import com.fluxguard.service.PagoService.SesionRequeridaException;
import com.fluxguard.service.PagoService.PagoNoDisponibleException;
import com.fluxguard.service.PayPalService.PayPalException;
import com.fluxguard.service.SesionService;
import com.fluxguard.service.StripeService;
import com.fluxguard.service.StripeService.StripeException;
import com.fluxguard.service.TicketPdf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Pago del carrito con PayPal, tarjeta (Stripe) o en tienda (Paynet): iniciar el pago, confirmarlo al regresar y cancelarlo.
 * Igual que el carrito, funciona con sesión o como invitado (encabezado X-Carrito-Invitado).
 */
@RestController
@RequestMapping("/api")
public class PagoController {

    private static final Logger log = LoggerFactory.getLogger(PagoController.class);
    // Orden de PayPal (p. ej. 5O190127TN364715T) o sesión de Stripe (cs_test_a1B2...)
    private static final Pattern REFERENCIA_PAGO = Pattern.compile("^[A-Za-z0-9_]{5,200}$");
    private static final Set<String> EVENTOS_STRIPE = Set.of(
            "checkout.session.completed", "checkout.session.async_payment_succeeded", "checkout.session.expired");

    private final PagoService pagoService;
    private final StripeService stripe;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SesionService sesionService;
    private final ObjectMapper json = new ObjectMapper();

    public PagoController(PagoService pagoService, StripeService stripe, PedidoRepository pedidoRepository,
                          UsuarioRepository usuarioRepository, SesionService sesionService) {
        this.pagoService = pagoService;
        this.stripe = stripe;
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.sesionService = sesionService;
    }

    /** Métodos de pago disponibles ("paypal", "stripe", "paynet"), para mostrar solo los botones que funcionan. */
    @GetMapping("/pagos/metodos")
    public ResponseEntity<ApiResponse<Void>> metodos() {
        ApiResponse<Void> response = ApiResponse.ok("Métodos de pago");
        response.setMetodosPago(pagoService.metodosDisponibles());
        return ResponseEntity.ok(response);
    }

    /**
     * Crea el pedido con el carrito actual (?metodo=paypal | stripe | paynet).
     * PayPal y Stripe devuelven la URL a la que hay que redirigir; Paynet devuelve el pedido con la referencia
     * y el código de barras de la ficha. Exige sesión iniciada: un invitado recibe 401 y debe iniciar sesión.
     */
    @PostMapping("/carrito/comprar")
    public ResponseEntity<ApiResponse<UsuarioDto>> comprar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                          @RequestHeader(value = CarritoController.INVITADO, required = false) String invitado,
                                                          @RequestParam(value = "metodo", defaultValue = PagoService.PAYPAL) String metodo) {
        Optional<Comprador> comprador = sesionService.compradorDesde(authorization, invitado);
        if (comprador.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Tu sesión expiró. Recarga la página."));
        }
        String proveedor = PagoService.STRIPE.equals(metodo) ? "Stripe" : PagoService.PAYNET.equals(metodo) ? "Paynet" : "PayPal";
        try {
            PagoService.Inicio inicio = pagoService.iniciar(comprador.get(), metodo);
            boolean ficha = inicio.pedido().getReferenciaPago() != null;
            ApiResponse<UsuarioDto> response = ApiResponse.ok(ficha
                    ? "Tu ficha de pago del pedido " + inicio.pedido().getFolio() + " está lista. Págala en cualquier tienda participante."
                    : inicio.urlPago() != null
                    ? "Te estamos llevando a " + proveedor + " para completar el pago."
                    : "¡Listo! Tu pedido " + inicio.pedido().getFolio() + " quedó confirmado.");
            response.setPedido(inicio.pedido());
            response.setUrlPago(inicio.urlPago());
            if (inicio.urlPago() == null) {
                usuarioRepository.findById(comprador.get().clave()).map(UsuarioDto::de).ifPresent(response::setUsuario);
            }
            return ResponseEntity.ok(response);
        } catch (SesionRequeridaException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
        } catch (CarritoException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (PagoNoDisponibleException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(e.getMessage()));
        } catch (PayPalException | StripeException | OpenpayException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("No pudimos conectar con " + proveedor + ". Intenta de nuevo en unos momentos."));
        }
    }

    /** Historial de compras del usuario con sesión, del más reciente al más antiguo. */
    @GetMapping("/pedidos")
    public ResponseEntity<ApiResponse<Void>> pedidos(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<String> usuarioId = sesionService.usuarioDesde(authorization).map(Usuario::getId);
        if (usuarioId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Inicia sesión para ver tu historial de compras."));
        }
        List<Pedido> pedidos = pedidoRepository.findByUsuarioIdOrderByFechaDesc(usuarioId.get());
        ApiResponse<Void> response = ApiResponse.ok("Historial de compras");
        response.setPedidos(pedidos);
        return ResponseEntity.ok(response);
    }

    /** Estado de un pedido del usuario. */
    @GetMapping("/pedidos/{folio}")
    public ResponseEntity<ApiResponse<UsuarioDto>> pedido(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                         @RequestHeader(value = CarritoController.INVITADO, required = false) String invitado,
                                                         @PathVariable String folio) {
        return conPedido(authorization, invitado, folio, pedido -> pedido);
    }

    /** Ticket del pedido en PDF. Solo para su dueño (con sesión o el invitado que compró) y cuando ya está pagado. */
    @GetMapping("/pedidos/{folio}/ticket")
    public ResponseEntity<?> ticket(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = CarritoController.INVITADO, required = false) String invitado,
                                    @PathVariable String folio) {
        Optional<Comprador> comprador = sesionService.compradorDesde(authorization, invitado);
        if (comprador.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Tu sesión expiró. Recarga la página."));
        }
        Optional<Pedido> pedido = pedidoRepository.findByFolio(folio)
                .filter(p -> p.getUsuarioId().equals(comprador.get().clave()));
        if (pedido.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("No encontramos ese pedido."));
        }
        if (!pedido.get().estaPagado()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("El ticket está disponible cuando se confirma el pago."));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(TicketPdf.nombreArchivo(pedido.get())).build().toString())
                .body(TicketPdf.generar(pedido.get()));
    }

    /** Al regresar de PayPal o Stripe, carrito.html manda el "token" (orden o sesión) y aquí se confirma el pago. */
    @PostMapping("/pedidos/{folio}/confirmar")
    public ResponseEntity<ApiResponse<UsuarioDto>> confirmar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @RequestHeader(value = CarritoController.INVITADO, required = false) String invitado,
                                                            @PathVariable String folio,
                                                            @RequestParam("token") String referencia) {
        if (!REFERENCIA_PAGO.matcher(referencia).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("El pago no es válido."));
        }
        return conPedido(authorization, invitado, folio, pedido -> pagoService.confirmar(pedido, referencia));
    }

    /** El cliente canceló en la página de pago. */
    @PostMapping("/pedidos/{folio}/cancelar")
    public ResponseEntity<ApiResponse<UsuarioDto>> cancelar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                           @RequestHeader(value = CarritoController.INVITADO, required = false) String invitado,
                                                           @PathVariable String folio) {
        return conPedido(authorization, invitado, folio, pagoService::cancelar);
    }

    /**
     * Webhook de Stripe. Se da de alta en Stripe (Developers > Webhooks) con la URL
     * https://tu-dominio/api/pagos/stripe/webhook y el evento checkout.session.completed.
     */
    @PostMapping("/pagos/stripe/webhook")
    public ResponseEntity<Void> webhookStripe(@RequestHeader(value = "Stripe-Signature", required = false) String firma,
                                              @RequestBody(required = false) String cuerpo) {
        if (!stripe.webhookConfigurado()) {
            return ResponseEntity.notFound().build();
        }
        if (!stripe.firmaValida(firma, cuerpo, Instant.now().getEpochSecond())) {
            log.warn("[FluxG] Webhook de Stripe con firma inválida.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        try {
            JsonNode evento = json.readTree(cuerpo);
            if (EVENTOS_STRIPE.contains(evento.path("type").asText())) {
                JsonNode sesion = evento.path("data").path("object");
                pagoService.confirmarDesdeStripe(sesion.path("client_reference_id").asText(null), sesion.path("id").asText(null));
            }
            return ResponseEntity.ok().build();
        } catch (StripeException e) {
            // Stripe reintenta el webhook si no respondemos 2xx
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            log.error("[FluxG] No se pudo procesar el webhook de Stripe: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
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
        } catch (PayPalException | StripeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("No pudimos confirmar el pago. Recarga la página en unos momentos."));
        }
    }
}
