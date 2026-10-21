package com.fluxguard;

import com.fluxguard.model.Carrito;
import com.fluxguard.model.Pedido;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.CarritoRepository;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.CarritoService;
import com.fluxguard.service.CatalogoService;
import com.fluxguard.service.Comprador;
import com.fluxguard.service.PagoService;
import com.fluxguard.service.PagoService.PagoNoDisponibleException;
import com.fluxguard.service.PayPalService;
import com.fluxguard.service.PayPalService.Captura;
import com.fluxguard.service.PayPalService.Orden;
import com.fluxguard.service.SuscripcionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagoServiceTests {

    private PayPalService payPal;
    private CarritoService carritoService;
    private PagoService pagoService;
    private Carrito carrito;
    private Usuario usuario;
    private Comprador comprador;
    private final Map<String, Pedido> pedidos = new HashMap<>();

    @BeforeEach
    void setUp() {
        CarritoRepository carritoRepository = mock(CarritoRepository.class);
        PedidoRepository pedidoRepository = mock(PedidoRepository.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        payPal = mock(PayPalService.class);

        usuario = new Usuario("Ana", "Lopez", "ana@fluxguard.io", "hash");
        usuario.setId("u1");
        comprador = Comprador.de(usuario);

        when(carritoRepository.findByUsuarioId(anyString())).thenAnswer(inv -> Optional.ofNullable(carrito).filter(c -> c.getUsuarioId().equals(inv.getArgument(0))));
        when(carritoRepository.save(any())).thenAnswer(inv -> carrito = inv.getArgument(0));
        when(pedidoRepository.save(any())).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) p.setId("pedido-" + (pedidos.size() + 1));
            pedidos.put(p.getId(), p);
            return p;
        });
        when(pedidoRepository.findByUsuarioIdOrderByFechaDesc("u1")).thenAnswer(inv -> List.copyOf(pedidos.values()));
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(payPal.estaConfigurado()).thenReturn(true);
        when(payPal.esSandbox()).thenReturn(true);
        when(payPal.getMoneda()).thenReturn("MXN");
        when(payPal.crearOrden(any())).thenReturn(new Orden("ORDEN123", "https://www.sandbox.paypal.com/checkoutnow?token=ORDEN123"));

        carritoService = new CarritoService(carritoRepository, pedidoRepository, new CatalogoService());
        pagoService = new PagoService(carritoService, payPal, pedidoRepository, usuarioRepository, new SuscripcionService());
    }

    private Pedido iniciarConKitYPlan() {
        carritoService.agregar(comprador, "kit-piloto", 1);
        carritoService.agregar(comprador, "plan-standard", 1);
        PagoService.Inicio inicio = pagoService.iniciar(comprador);
        assertEquals("https://www.sandbox.paypal.com/checkoutnow?token=ORDEN123", inicio.urlPago());
        assertEquals(Pedido.PENDIENTE, inicio.pedido().getEstado());
        return inicio.pedido();
    }

    @Test
    void cobroCompletadoMarcaPagadoActivaPlanYVaciaCarrito() {
        Pedido pedido = iniciarConKitYPlan();
        when(payPal.capturar("ORDEN123")).thenReturn(new Captura("CAP1", "COMPLETED", new BigDecimal("849.00"), "MXN"));

        pagoService.confirmar(pedido, "ORDEN123");

        assertEquals(Pedido.PAGADO, pedido.getEstado());
        assertEquals("CAP1", pedido.getPagoId());
        assertEquals("paypal_sandbox", pedido.getMetodoPago());
        assertEquals("standard", usuario.getSuscripcion().getTipo());
        assertEquals(pedido.getFolio(), usuario.getSuscripcion().getTransaccionId());
        assertTrue(carrito.getItems().isEmpty());

        // Recargar la página no vuelve a cobrar
        pagoService.confirmar(pedido, "ORDEN123");
        verify(payPal, times(1)).capturar("ORDEN123");
    }

    @Test
    void montoDistintoNoSeMarcaComoPagado() {
        Pedido pedido = iniciarConKitYPlan();
        when(payPal.capturar("ORDEN123")).thenReturn(new Captura("CAP1", "COMPLETED", new BigDecimal("1.00"), "MXN"));

        pagoService.confirmar(pedido, "ORDEN123");

        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
        assertNull(usuario.getSuscripcion());
        assertFalse(carrito.getItems().isEmpty());
    }

    @Test
    void ordenDeOtroPedidoSeIgnora() {
        Pedido pedido = iniciarConKitYPlan();

        pagoService.confirmar(pedido, "OTRAORDEN9");

        verify(payPal, never()).capturar(any());
        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
    }

    @Test
    void cobroRechazadoOCanceladoConservaElCarrito() {
        Pedido pedido = iniciarConKitYPlan();
        when(payPal.capturar("ORDEN123")).thenReturn(new Captura(null, "DECLINED", null, null));

        pagoService.confirmar(pedido, "ORDEN123");
        assertEquals(Pedido.RECHAZADO, pedido.getEstado());
        assertEquals(2, carrito.getItems().size());

        Pedido otro = pagoService.iniciar(comprador).pedido();
        pagoService.cancelar(otro);
        assertEquals(Pedido.CANCELADO, otro.getEstado());
        assertEquals(2, carrito.getItems().size());
    }

    @Test
    void pedidoGratisSeConfirmaSinPayPal() {
        when(payPal.estaConfigurado()).thenReturn(false);
        carritoService.agregar(comprador, "plan-free", 1);

        PagoService.Inicio inicio = pagoService.iniciar(comprador);

        assertNull(inicio.urlPago());
        assertEquals(Pedido.PAGADO, inicio.pedido().getEstado());
        assertEquals("free", usuario.getSuscripcion().getTipo());
        verify(payPal, never()).crearOrden(any());
    }

    @Test
    void sinPayPalConfiguradoNoSeCreanPedidos() {
        when(payPal.estaConfigurado()).thenReturn(false);
        carritoService.agregar(comprador, "kit-piloto", 1);

        assertThrows(PagoNoDisponibleException.class, () -> pagoService.iniciar(comprador));
        assertTrue(pedidos.isEmpty());
    }

    @Test
    void invitadoPagaConPayPalYSeGuardanSusDatos() {
        Comprador invitado = Comprador.invitado("token-de-invitado-0123456789");
        carritoService.agregar(invitado, "kit-piloto", 1);
        Pedido pedido = pagoService.iniciar(invitado).pedido();
        when(payPal.capturar("ORDEN123")).thenReturn(new Captura("CAP9", "COMPLETED", new BigDecimal("350.00"), "MXN",
                "Juan Prueba", "comprador@personal.example.com"));

        pagoService.confirmar(pedido, "ORDEN123");

        assertEquals(Pedido.PAGADO, pedido.getEstado());
        assertEquals("Juan Prueba", pedido.getNombreCliente());
        assertEquals("comprador@personal.example.com", pedido.getCorreoCliente());
        assertNull(usuario.getSuscripcion()); // nadie con cuenta recibió un plan
    }
}
