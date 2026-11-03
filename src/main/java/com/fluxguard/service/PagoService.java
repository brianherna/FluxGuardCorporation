package com.fluxguard.service;

import com.fluxguard.model.Pedido;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.OpenpayService.Cargo;
import com.fluxguard.service.PayPalService.Captura;
import com.fluxguard.service.PayPalService.Orden;
import com.fluxguard.service.StripeService.Sesion;
import com.fluxguard.service.StripeService.SesionPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Flujo de pago (PayPal o tarjeta con Stripe):
 * 1. iniciar(): crea el pedido PENDIENTE y la orden de PayPal o la sesión de Stripe; el navegador va a pagar ahí.
 * 2. El proveedor regresa al cliente a carrito.html?pedido=FOLIO&token=REFERENCIA y confirmar() comprueba el pago:
 *    - PayPal: cobra (captura) la orden aprobada. Si el cliente no regresa, no se cobra nada.
 *    - Stripe: el cobro ya ocurrió en su página; se consulta la sesión. El webhook de Stripe hace lo mismo
 *      por si el cliente cierra la ventana antes de regresar.
 *    Si el pago está completo y el monto coincide, el pedido pasa a PAGADO, se activa el plan y se vacía el carrito.
 * En tienda (Paynet) el cliente no regresa: iniciar() entrega la ficha y el webhook de Openpay confirma el pago
 * consultando el cargo (confirmarDesdeOpenpay).
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    public record Inicio(Pedido pedido, String urlPago) {}

    /** El pago no se puede iniciar (p. ej. PayPal sin configurar). */
    public static class PagoNoDisponibleException extends RuntimeException {
        public PagoNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }

    /** Solo se puede comprar con sesión iniciada (el carrito sí se puede llenar como invitado). */
    public static class SesionRequeridaException extends RuntimeException {
        public SesionRequeridaException() {
            super("Inicia sesión para completar tu compra.");
        }
    }

    private final CarritoService carritoService;
    public static final String PAYPAL = "paypal";
    public static final String STRIPE = "stripe";
    public static final String PAYNET = "paynet";
    private static final String CARGO_COMPLETADO = "completed";

    private final PayPalService payPal;
    private final StripeService stripe;
    private final OpenpayService openpay;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SuscripcionService suscripcionService;
    private final CorreoService correoService;
    private final NotificacionService notificacionService;

    public PagoService(CarritoService carritoService, PayPalService payPal, StripeService stripe, OpenpayService openpay,
                       PedidoRepository pedidoRepository, UsuarioRepository usuarioRepository,
                       SuscripcionService suscripcionService, CorreoService correoService,
                       NotificacionService notificacionService) {
        this.carritoService = carritoService;
        this.payPal = payPal;
        this.stripe = stripe;
        this.openpay = openpay;
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.suscripcionService = suscripcionService;
        this.correoService = correoService;
        this.notificacionService = notificacionService;
    }

    /** Métodos de pago configurados en el servidor, para que carrito.html solo muestre los que funcionan. */
    public List<String> metodosDisponibles() {
        List<String> metodos = new ArrayList<>();
        if (payPal.estaConfigurado()) metodos.add(PAYPAL);
        if (stripe.estaConfigurado()) metodos.add(STRIPE);
        if (openpay.estaConfigurado()) metodos.add(PAYNET);
        return metodos;
    }

    /**
     * Crea el pedido del carrito y el pago en el proveedor elegido ("paypal", "stripe" o "paynet").
     * Solo con sesión iniciada: al crear la cuenta el cliente aceptó las políticas de compra, envío y devoluciones.
     * Un pedido de $0 (solo Plan Free) se completa sin cobro.
     *
     * @param dispositivo desde dónde se compra (NotificacionService.origenDesde); se muestra en el aviso de la compra
     */
    public Inicio iniciar(Comprador comprador, String metodo, String dispositivo) {
        if (comprador.esInvitado()) {
            throw new SesionRequeridaException();
        }
        String elegido = STRIPE.equals(metodo) ? STRIPE : PAYNET.equals(metodo) ? PAYNET : PAYPAL;
        boolean disponible = switch (elegido) {
            case STRIPE -> stripe.estaConfigurado();
            case PAYNET -> openpay.estaConfigurado();
            default -> payPal.estaConfigurado();
        };
        // Se revisa antes de crear el pedido para no dejar pedidos pendientes que nadie puede pagar
        if (!disponible && carritoService.ver(comprador).total() > 0) {
            throw new PagoNoDisponibleException("Ese método de pago no está disponible en este momento. Intenta con otro.");
        }

        Pedido pedido = carritoService.crearPedido(comprador);
        pedido.setDispositivo(dispositivo);
        if (pedido.getTotal() == 0) {
            pedido.setMetodoPago("sin_costo");
            completar(pedido);
            return new Inicio(pedido, null);
        }

        if (PAYNET.equals(elegido)) {
            Usuario cliente = comprador.usuario();
            OpenpayService.Ficha ficha = openpay.crearFicha(pedido, cliente.getNombre(), cliente.getApellido(), cliente.getCorreo());
            pedido.setOrdenId(ficha.cargoId());
            pedido.setReferenciaPago(ficha.referencia());
            pedido.setCodigoBarrasUrl(ficha.codigoBarrasUrl());
            pedido.setUrlPago(ficha.urlPdf());
            pedido.setVencePago(ficha.vence());
            pedido.setMetodoPago(openpay.esSandbox() ? "paynet_sandbox" : "paynet");
            pedido.setNombreCliente((cliente.getNombre() + " " + (cliente.getApellido() == null ? "" : cliente.getApellido())).trim());
            pedido.setCorreoCliente(cliente.getCorreo());
            pedidoRepository.save(pedido);
            // El pedido queda apartado esperando el pago en tienda: el carrito se vacía como en cualquier ficha de pago
            carritoService.vaciar(comprador.clave());
            return new Inicio(pedido, null);
        }

        if (STRIPE.equals(elegido)) {
            Sesion sesion = stripe.crearSesion(pedido);
            pedido.setOrdenId(sesion.id());
            pedido.setUrlPago(sesion.url());
            pedido.setMetodoPago(stripe.esPrueba() ? "stripe_test" : "stripe");
            pedidoRepository.save(pedido);
            return new Inicio(pedido, sesion.url());
        }

        Orden orden = payPal.crearOrden(pedido);
        pedido.setOrdenId(orden.id());
        pedido.setUrlPago(orden.urlAprobacion());
        pedido.setMetodoPago(payPal.esSandbox() ? "paypal_sandbox" : "paypal");
        pedidoRepository.save(pedido);
        return new Inicio(pedido, orden.urlAprobacion());
    }

    /**
     * Comprueba el pago del pedido con su proveedor. Es idempotente: recargar la página o recibir
     * el mismo webhook varias veces no cobra ni activa nada dos veces.
     *
     * @param referencia el "token" con el que regresa el cliente (orden de PayPal, sesión de Stripe o cargo de Openpay); debe ser la de este pedido
     */
    public Pedido confirmar(Pedido pedido, String referencia) {
        if (!Pedido.PENDIENTE.equals(pedido.getEstado()) || referencia == null || !referencia.equals(pedido.getOrdenId())) {
            return pedido;
        }
        if (pedido.getMetodoPago() != null && pedido.getMetodoPago().startsWith(STRIPE)) {
            return confirmarStripe(pedido, referencia);
        }
        if (pedido.getMetodoPago() != null && pedido.getMetodoPago().startsWith(PAYNET)) {
            // Una ficha no se captura: solo se consulta si ya se pagó en tienda
            return openpay.obtenerCargo(referencia).flatMap(this::registrarCargo).orElse(pedido);
        }
        String ordenId = referencia;

        Captura captura = payPal.capturar(ordenId);
        switch (captura.status() == null ? "" : captura.status()) {
            case "COMPLETED" -> {
                if (!montoCoincide(pedido, captura)) {
                    log.error("[FluxG] El cobro {} no coincide con el pedido {}: {} {} contra {} {}. No se marca como pagado.",
                            captura.id(), pedido.getFolio(), captura.monto(), captura.moneda(), pedido.getTotal(), payPal.getMoneda());
                    return pedido;
                }
                pedido.setPagoId(captura.id());
                if (pedido.getCorreoCliente() == null) {
                    // Compra como invitado: los datos de contacto son los de su cuenta de PayPal
                    pedido.setNombreCliente(captura.nombrePagador());
                    pedido.setCorreoCliente(captura.correoPagador());
                }
                completar(pedido);
            }
            case "PENDING" -> {
                // PayPal retuvo el cobro para revisión; el pedido sigue pendiente
                pedido.setPagoId(captura.id());
                pedidoRepository.save(pedido);
            }
            default -> {
                pedido.setEstado(Pedido.RECHAZADO);
                pedido.setPagoId(captura.id());
                pedidoRepository.save(pedido);
            }
        }
        log.info("[FluxG] Pedido {}: cobro PayPal {} -> {}", pedido.getFolio(), captura.status(), pedido.getEstado());
        return pedido;
    }

    /** Webhook de Stripe (checkout.session.completed): confirma el pedido aunque el cliente no haya regresado a la página. */
    public void confirmarDesdeStripe(String pedidoId, String sesionId) {
        if (pedidoId == null || sesionId == null) {
            return;
        }
        pedidoRepository.findById(pedidoId).ifPresentOrElse(
                pedido -> confirmar(pedido, sesionId),
                () -> log.warn("[FluxG] Webhook de Stripe para un pedido que no existe ({}).", pedidoId));
    }

    /**
     * Webhook de Openpay (charge.succeeded): confirma el pedido pagado en tienda. Del evento solo se usa el id del cargo;
     * estado, monto y pedido salen de consultar ese cargo a Openpay, porque el webhook no viene firmado.
     */
    public void confirmarDesdeOpenpay(String cargoId) {
        if (cargoId == null) {
            return;
        }
        openpay.obtenerCargo(cargoId).ifPresentOrElse(
                this::registrarCargo,
                () -> log.warn("[FluxG] Webhook de Openpay para un cargo que Openpay no conoce ({}).", cargoId));
    }

    /**
     * Aplica al pedido un cargo ya consultado a Openpay y devuelve el pedido, si existe.
     * Openpay puede entregar el mismo evento dos veces a la vez: es synchronized y relee el pedido
     * para que solo una entrega lo vea PENDIENTE y envíe el ticket y el aviso.
     */
    private synchronized Optional<Pedido> registrarCargo(Cargo cargo) {
        Optional<Pedido> encontrado = cargo.folio() == null ? Optional.empty() : pedidoRepository.findByFolio(cargo.folio());
        if (encontrado.isEmpty()) {
            log.warn("[FluxG] El cargo de Openpay {} es de un pedido que no existe ({}).", cargo.id(), cargo.folio());
            return encontrado;
        }
        Pedido pedido = encontrado.get();
        if (pedido.getOrdenId() == null || !pedido.getOrdenId().equals(cargo.id())) {
            log.error("[FluxG] El cargo de Openpay {} no pertenece al pedido {}.", cargo.id(), pedido.getFolio());
            return encontrado;
        }
        if (!CARGO_COMPLETADO.equals(cargo.estado())) {
            // "in_progress": la ficha sigue sin pagarse; el pedido sigue pendiente
            log.info("[FluxG] Pedido {}: cargo Openpay {} en estado {}.", pedido.getFolio(), cargo.id(), cargo.estado());
            return encontrado;
        }
        if (!Pedido.PENDIENTE.equals(pedido.getEstado())) {
            if (!pedido.estaPagado()) {
                log.error("[FluxG] El cliente pagó en tienda el pedido {}, que está {}. Hay que revisarlo a mano.", pedido.getFolio(), pedido.getEstado());
            }
            return encontrado;
        }
        if (!montoCoincide(pedido, cargo)) {
            log.error("[FluxG] El cargo de Openpay {} no coincide con el pedido {}: {} {} contra {} MXN. No se marca como pagado.",
                    cargo.id(), pedido.getFolio(), cargo.monto(), cargo.moneda(), pedido.getTotal());
            return encontrado;
        }
        pedido.setPagoId(cargo.id());
        completar(pedido);
        log.info("[FluxG] Pedido {}: cargo Openpay {} -> {}", pedido.getFolio(), cargo.id(), pedido.getEstado());
        return encontrado;
    }

    private Pedido confirmarStripe(Pedido pedido, String sesionId) {
        SesionPago sesion = stripe.obtenerSesion(sesionId);
        if (!pedido.getId().equals(sesion.referencia())) {
            log.error("[FluxG] La sesión de Stripe {} no pertenece al pedido {}.", sesionId, pedido.getFolio());
            return pedido;
        }
        if (sesion.pagado()) {
            if (!"mxn".equalsIgnoreCase(sesion.moneda()) || sesion.montoCentavos() != pedido.getTotal() * 100L) {
                log.error("[FluxG] El pago de Stripe {} no coincide con el pedido {}: {} {} contra {} MXN. No se marca como pagado.",
                        sesionId, pedido.getFolio(), sesion.montoCentavos(), sesion.moneda(), pedido.getTotal());
                return pedido;
            }
            pedido.setPagoId(sesion.pagoId());
            if (pedido.getCorreoCliente() == null) {
                // Compra como invitado: los datos de contacto son los que capturó en la página de Stripe
                pedido.setNombreCliente(sesion.nombre());
                pedido.setCorreoCliente(sesion.correo());
            }
            completar(pedido);
        } else if ("expired".equals(sesion.estado())) {
            pedido.setEstado(Pedido.CANCELADO);
            pedidoRepository.save(pedido);
        }
        // "open" sin pagar: el cliente aún no termina; el pedido sigue pendiente
        log.info("[FluxG] Pedido {}: sesión Stripe {} (pagado={}) -> {}", pedido.getFolio(), sesion.estado(), sesion.pagado(), pedido.getEstado());
        return pedido;
    }

    /** El cliente canceló en la página de pago: el pedido se cierra y su carrito queda igual para intentarlo de nuevo. */
    public Pedido cancelar(Pedido pedido) {
        if (Pedido.PENDIENTE.equals(pedido.getEstado())) {
            pedido.setEstado(Pedido.CANCELADO);
            pedidoRepository.save(pedido);
        }
        return pedido;
    }

    /** Marca el pedido como pagado, activa el plan que incluya, vacía el carrito, envía el ticket y avisa al dueño de la cuenta. */
    private void completar(Pedido pedido) {
        pedido.setEstado(Pedido.PAGADO);
        pedido.setPagadoEn(LocalDateTime.now());
        pedidoRepository.save(pedido);

        // Los invitados no tienen cuenta: no hay plan que activar
        usuarioRepository.findById(pedido.getUsuarioId()).ifPresent(usuario -> {
            String plan = null;
            boolean incluyeKit = false;
            for (Pedido.Linea linea : pedido.getLineas()) {
                if (linea.productoId().startsWith("plan-")) {
                    plan = linea.productoId().substring("plan-".length());
                }
                if (linea.productoId().equals(CatalogoService.KIT_PILOTO)) {
                    incluyeKit = true;
                }
            }
            if (plan != null) {
                suscripcionService.activar(usuario, plan, pedido.getFolio());
            } else if (incluyeKit && usuario.getSuscripcion() == null) {
                // El kit piloto incluye el dashboard de monitoreo
                suscripcionService.activar(usuario, "prototipo", pedido.getFolio());
            }
            usuarioRepository.save(usuario);
        });

        carritoService.vaciar(pedido.getUsuarioId());
        enviarTicket(pedido);
        notificacionService.avisarCompra(pedido);
    }

    /** El ticket se envía una sola vez, al pasar a PAGADO. Si el correo falla, el pago sigue confirmado. */
    private void enviarTicket(Pedido pedido) {
        if (pedido.getCorreoCliente() == null || pedido.getCorreoCliente().isBlank()) {
            log.warn("[FluxG] Pedido {} pagado sin correo del cliente: no se envió el ticket.", pedido.getFolio());
            return;
        }
        try {
            correoService.enviarTicket(pedido);
        } catch (RuntimeException e) {
            log.error("[FluxG] No se pudo enviar el ticket del pedido {} a {}: {}", pedido.getFolio(), pedido.getCorreoCliente(), e.getMessage());
        }
    }

    private boolean montoCoincide(Pedido pedido, Captura captura) {
        return captura.monto() != null
                && payPal.getMoneda().equals(captura.moneda())
                && captura.monto().compareTo(BigDecimal.valueOf(pedido.getTotal())) == 0;
    }

    private boolean montoCoincide(Pedido pedido, Cargo cargo) {
        return cargo.monto() != null
                && "MXN".equalsIgnoreCase(cargo.moneda())
                && cargo.monto().compareTo(BigDecimal.valueOf(pedido.getTotal())) == 0;
    }
}
