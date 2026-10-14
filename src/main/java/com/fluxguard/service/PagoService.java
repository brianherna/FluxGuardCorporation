package com.fluxguard.service;

import com.fluxguard.model.Pedido;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.PayPalService.Captura;
import com.fluxguard.service.PayPalService.Orden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Flujo de pago con PayPal:
 * 1. iniciar(): crea el pedido PENDIENTE y la orden de PayPal; el navegador va a PayPal a aprobar el pago.
 * 2. PayPal regresa al cliente a carrito.html?pedido=FOLIO&token=ORDEN y confirmar() cobra la orden.
 *    Si el cobro se completa y el monto coincide, el pedido pasa a PAGADO, se activa el plan y se vacía el carrito.
 * Si el cliente aprueba pero nunca regresa, la orden no se cobra: PayPal solo cobra al capturar.
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

    private final CarritoService carritoService;
    private final PayPalService payPal;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SuscripcionService suscripcionService;

    public PagoService(CarritoService carritoService, PayPalService payPal, PedidoRepository pedidoRepository,
                       UsuarioRepository usuarioRepository, SuscripcionService suscripcionService) {
        this.carritoService = carritoService;
        this.payPal = payPal;
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.suscripcionService = suscripcionService;
    }

    /** Crea el pedido del carrito y la orden de PayPal. Un pedido de $0 (solo Plan Free) se completa sin cobro. */
    public Inicio iniciar(Comprador comprador) {
        // Se revisa antes de crear el pedido para no dejar pedidos pendientes que nadie puede pagar
        if (!payPal.estaConfigurado() && carritoService.ver(comprador).total() > 0) {
            throw new PagoNoDisponibleException("Los pagos no están disponibles en este momento. Intenta más tarde.");
        }

        Pedido pedido = carritoService.crearPedido(comprador);
        if (pedido.getTotal() == 0) {
            pedido.setMetodoPago("sin_costo");
            completar(pedido);
            return new Inicio(pedido, null);
        }

        Orden orden = payPal.crearOrden(pedido);
        pedido.setOrdenId(orden.id());
        pedido.setUrlPago(orden.urlAprobacion());
        pedido.setMetodoPago(payPal.esSandbox() ? "paypal_sandbox" : "paypal");
        pedidoRepository.save(pedido);
        return new Inicio(pedido, orden.urlAprobacion());
    }

    /**
     * Cobra la orden que el cliente aprobó en PayPal. Es idempotente: si el cliente recarga la página
     * no se cobra dos veces (PayPal devuelve el cobro existente).
     *
     * @param ordenId el parámetro "token" con el que PayPal regresa al cliente; debe ser la orden de este pedido
     */
    public Pedido confirmar(Pedido pedido, String ordenId) {
        if (!Pedido.PENDIENTE.equals(pedido.getEstado()) || ordenId == null || !ordenId.equals(pedido.getOrdenId())) {
            return pedido;
        }

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

    /** El cliente canceló en PayPal: el pedido se cierra y su carrito queda igual para intentarlo de nuevo. */
    public Pedido cancelar(Pedido pedido) {
        if (Pedido.PENDIENTE.equals(pedido.getEstado())) {
            pedido.setEstado(Pedido.CANCELADO);
            pedidoRepository.save(pedido);
        }
        return pedido;
    }

    /** Marca el pedido como pagado, activa el plan que incluya y vacía el carrito del usuario. */
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
    }

    private boolean montoCoincide(Pedido pedido, Captura captura) {
        return captura.monto() != null
                && payPal.getMoneda().equals(captura.moneda())
                && captura.monto().compareTo(BigDecimal.valueOf(pedido.getTotal())) == 0;
    }
}
