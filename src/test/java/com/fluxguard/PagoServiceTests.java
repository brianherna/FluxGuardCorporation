package com.fluxguard;

import com.fluxguard.controller.PagoController;
import com.fluxguard.model.Carrito;
import com.fluxguard.model.Pedido;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.CarritoRepository;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.CarritoService;
import com.fluxguard.service.CatalogoService;
import com.fluxguard.service.Comprador;
import com.fluxguard.service.CorreoService;
import com.fluxguard.service.NotificacionService;
import com.fluxguard.service.PagoService;
import com.fluxguard.service.PagoService.PagoNoDisponibleException;
import com.fluxguard.service.PayPalService;
import com.fluxguard.service.PayPalService.Captura;
import com.fluxguard.service.PayPalService.Orden;
import com.fluxguard.service.OpenpayService;
import com.fluxguard.service.SesionService;
import com.fluxguard.service.StripeService;
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
    private StripeService stripe;
    private CorreoService correoService;
    private OpenpayService openpay;
    private NotificacionService notificacionService;
    private CarritoService carritoService;
    private PagoService pagoService;
    private PedidoRepository pedidoRepository;
    private UsuarioRepository usuarioRepository;
    private Carrito carrito;
    private Usuario usuario;
    private Comprador comprador;
    private final Map<String, Pedido> pedidos = new HashMap<>();

    @BeforeEach
    void setUp() {
        CarritoRepository carritoRepository = mock(CarritoRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        payPal = mock(PayPalService.class);
        stripe = mock(StripeService.class);
        correoService = mock(CorreoService.class);
        openpay = mock(OpenpayService.class);
        notificacionService = mock(NotificacionService.class);

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
        when(pedidoRepository.findByFolio(anyString())).thenAnswer(inv -> pedidos.values().stream()
                .filter(p -> p.getFolio().equals(inv.getArgument(0))).findFirst());
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(payPal.estaConfigurado()).thenReturn(true);
        when(payPal.esSandbox()).thenReturn(true);
        when(payPal.getMoneda()).thenReturn("MXN");
        when(payPal.crearOrden(any())).thenReturn(new Orden("ORDEN123", "https://www.sandbox.paypal.com/checkoutnow?token=ORDEN123"));

        carritoService = new CarritoService(carritoRepository, pedidoRepository, new CatalogoService());
        pagoService = new PagoService(carritoService, payPal, stripe, openpay, pedidoRepository, usuarioRepository,
                new SuscripcionService(), correoService, notificacionService);
    }

    private Pedido iniciarConKitYPlan() {
        carritoService.agregar(comprador, "kit-piloto", 1);
        carritoService.agregar(comprador, "plan-standard", 1);
        PagoService.Inicio inicio = pagoService.iniciar(comprador, PagoService.PAYPAL, "pc");
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

        // Recargar la página no vuelve a cobrar ni a enviar el ticket o el aviso
        pagoService.confirmar(pedido, "ORDEN123");
        verify(payPal, times(1)).capturar("ORDEN123");
        verify(correoService, times(1)).enviarTicket(pedido);
        verify(notificacionService, times(1)).avisarCompra(pedido);
    }

    @Test
    void siElCorreoDelTicketFallaElPagoSigueConfirmado() {
        Pedido pedido = iniciarConKitYPlan();
        when(payPal.capturar("ORDEN123")).thenReturn(new Captura("CAP1", "COMPLETED", new BigDecimal("849.00"), "MXN"));
        doThrow(new org.springframework.web.client.RestClientException("sin conexión")).when(correoService).enviarTicket(any());

        pagoService.confirmar(pedido, "ORDEN123");

        assertEquals(Pedido.PAGADO, pedido.getEstado());
        assertEquals("standard", usuario.getSuscripcion().getTipo());
    }

    @Test
    void montoDistintoNoSeMarcaComoPagado() {
        Pedido pedido = iniciarConKitYPlan();
        when(payPal.capturar("ORDEN123")).thenReturn(new Captura("CAP1", "COMPLETED", new BigDecimal("1.00"), "MXN"));

        pagoService.confirmar(pedido, "ORDEN123");

        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
        assertNull(usuario.getSuscripcion());
        assertFalse(carrito.getItems().isEmpty());
        verify(correoService, never()).enviarTicket(any());
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

        Pedido otro = pagoService.iniciar(comprador, PagoService.PAYPAL, "pc").pedido();
        pagoService.cancelar(otro);
        assertEquals(Pedido.CANCELADO, otro.getEstado());
        assertEquals(2, carrito.getItems().size());
    }

    @Test
    void pedidoGratisSeConfirmaSinPayPal() {
        when(payPal.estaConfigurado()).thenReturn(false);
        carritoService.agregar(comprador, "plan-free", 1);

        PagoService.Inicio inicio = pagoService.iniciar(comprador, PagoService.PAYPAL, "pc");

        assertNull(inicio.urlPago());
        assertEquals(Pedido.PAGADO, inicio.pedido().getEstado());
        assertEquals("free", usuario.getSuscripcion().getTipo());
        verify(payPal, never()).crearOrden(any());
    }

    @Test
    void sinPayPalConfiguradoNoSeCreanPedidos() {
        when(payPal.estaConfigurado()).thenReturn(false);
        carritoService.agregar(comprador, "kit-piloto", 1);

        assertThrows(PagoNoDisponibleException.class, () -> pagoService.iniciar(comprador, PagoService.PAYPAL, "pc"));
        assertTrue(pedidos.isEmpty());
    }

    private Pedido iniciarConStripe() {
        when(stripe.estaConfigurado()).thenReturn(true);
        when(stripe.esPrueba()).thenReturn(true);
        when(stripe.crearSesion(any())).thenReturn(new StripeService.Sesion("cs_test_123", "https://checkout.stripe.com/c/pay/cs_test_123"));
        carritoService.agregar(comprador, "kit-piloto", 1);
        carritoService.agregar(comprador, "plan-standard", 1);
        PagoService.Inicio inicio = pagoService.iniciar(comprador, PagoService.STRIPE, "pc");
        assertEquals("https://checkout.stripe.com/c/pay/cs_test_123", inicio.urlPago());
        assertEquals("stripe_test", inicio.pedido().getMetodoPago());
        verify(payPal, never()).crearOrden(any());
        return inicio.pedido();
    }

    @Test
    void pagoConTarjetaStripeMarcaPagadoYActivaPlan() {
        Pedido pedido = iniciarConStripe();
        when(stripe.obtenerSesion("cs_test_123")).thenReturn(new StripeService.SesionPago(
                "cs_test_123", pedido.getId(), true, "complete", 84900, "mxn", "pi_1", "Ana Lopez", "ana@fluxguard.io"));

        pagoService.confirmar(pedido, "cs_test_123");

        assertEquals(Pedido.PAGADO, pedido.getEstado());
        assertEquals("pi_1", pedido.getPagoId());
        assertEquals("standard", usuario.getSuscripcion().getTipo());
        assertTrue(carrito.getItems().isEmpty());

        // El webhook llega después: no vuelve a procesar nada
        pagoService.confirmar(pedido, "cs_test_123");
        verify(stripe, times(1)).obtenerSesion("cs_test_123");
    }

    @Test
    void stripeSinPagarOConMontoDistintoNoSeMarcaPagado() {
        Pedido pedido = iniciarConStripe();
        when(stripe.obtenerSesion("cs_test_123")).thenReturn(new StripeService.SesionPago(
                "cs_test_123", pedido.getId(), false, "open", 84900, "mxn", null, null, null));
        pagoService.confirmar(pedido, "cs_test_123");
        assertEquals(Pedido.PENDIENTE, pedido.getEstado());

        when(stripe.obtenerSesion("cs_test_123")).thenReturn(new StripeService.SesionPago(
                "cs_test_123", pedido.getId(), true, "complete", 100, "mxn", "pi_1", null, null));
        pagoService.confirmar(pedido, "cs_test_123");
        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
        assertNull(usuario.getSuscripcion());
    }

    @Test
    void sesionDeStripeDeOtroPedidoSeIgnora() {
        Pedido pedido = iniciarConStripe();
        when(stripe.obtenerSesion("cs_test_123")).thenReturn(new StripeService.SesionPago(
                "cs_test_123", "otro-pedido", true, "complete", 84900, "mxn", "pi_1", null, null));

        pagoService.confirmar(pedido, "cs_test_123");

        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
    }

    @Test
    void soloSeOfrecenLosMetodosConfigurados() {
        when(stripe.estaConfigurado()).thenReturn(false);
        assertEquals(List.of(PagoService.PAYPAL), pagoService.metodosDisponibles());
        when(stripe.estaConfigurado()).thenReturn(true);
        assertEquals(List.of(PagoService.PAYPAL, PagoService.STRIPE), pagoService.metodosDisponibles());
    }

    @Test
    void firmaDelWebhookDeStripe() throws Exception {
        StripeService real = new StripeService("sk_test_x", "whsec_prueba", "http://localhost:8080");
        String cuerpo = "{\"type\":\"checkout.session.completed\"}";
        long t = 1_800_000_000L;
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("whsec_prueba".getBytes(), "HmacSHA256"));
        String firma = java.util.HexFormat.of().formatHex(mac.doFinal((t + "." + cuerpo).getBytes()));

        assertTrue(real.firmaValida("t=" + t + ",v1=" + firma, cuerpo, t + 10));
        assertFalse(real.firmaValida("t=" + t + ",v1=" + firma, cuerpo + " ", t + 10)); // cuerpo alterado
        assertFalse(real.firmaValida("t=" + t + ",v1=" + firma, cuerpo, t + 1000));     // evento viejo
        assertFalse(real.firmaValida(null, cuerpo, t));
    }

    private void paynetConfigurado() {
        when(openpay.estaConfigurado()).thenReturn(true);
        when(openpay.esSandbox()).thenReturn(true);
        when(openpay.crearFicha(any(), any(), any(), any())).thenReturn(new OpenpayService.Ficha(
                "tr_123", "1010101234567890", "https://sandbox-api.openpay.mx/barcode/1010101234567890",
                "https://sandbox-dashboard.openpay.mx/paynet-pdf/m1/1010101234567890", java.time.LocalDateTime.now().plusHours(72)));
    }

    @Test
    void pagoEnTiendaGeneraFichaYVaciaElCarritoSinActivarNada() {
        paynetConfigurado();
        carritoService.agregar(comprador, "kit-piloto", 1);

        PagoService.Inicio inicio = pagoService.iniciar(comprador, PagoService.PAYNET, "pc");

        Pedido pedido = inicio.pedido();
        assertNull(inicio.urlPago());
        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
        assertEquals("paynet_sandbox", pedido.getMetodoPago());
        assertEquals("1010101234567890", pedido.getReferenciaPago());
        assertTrue(pedido.getUrlPago().endsWith("/paynet-pdf/m1/1010101234567890"));
        assertNotNull(pedido.getVencePago());
        // Con sesión se usan los datos de la cuenta
        verify(openpay).crearFicha(any(), eq("Ana"), eq("Lopez"), eq("ana@fluxguard.io"));
        assertTrue(carrito.getItems().isEmpty());
        assertNull(usuario.getSuscripcion()); // aún no se paga: no se activa el dashboard
        verify(payPal, never()).crearOrden(any());
    }

    @Test
    void sinOpenpayNoSeOfreceElPagoEnTienda() {
        when(openpay.estaConfigurado()).thenReturn(false);
        carritoService.agregar(comprador, "kit-piloto", 1);

        assertFalse(pagoService.metodosDisponibles().contains(PagoService.PAYNET));
        assertThrows(PagoService.PagoNoDisponibleException.class, () -> pagoService.iniciar(comprador, PagoService.PAYNET, "pc"));
    }

    @Test
    void sinSesionNoSePuedeComprar() {
        paynetConfigurado();
        Comprador invitado = Comprador.invitado("token-de-invitado-0123456789");
        carritoService.agregar(invitado, "kit-piloto", 1); // llenar el carrito sí se puede

        for (String metodo : List.of(PagoService.PAYPAL, PagoService.STRIPE, PagoService.PAYNET)) {
            assertThrows(PagoService.SesionRequeridaException.class, () -> pagoService.iniciar(invitado, metodo, "pc"));
        }
        assertTrue(pedidos.isEmpty());
        verify(payPal, never()).crearOrden(any());
        verify(openpay, never()).crearFicha(any(), any(), any(), any());
    }

    private Pedido iniciarConPaynet() {
        paynetConfigurado();
        carritoService.agregar(comprador, "kit-piloto", 1);
        carritoService.agregar(comprador, "plan-standard", 1);
        return pagoService.iniciar(comprador, PagoService.PAYNET, "pc").pedido();
    }

    private OpenpayService.Cargo cargo(Pedido pedido, String estado, String monto) {
        return new OpenpayService.Cargo("tr_123", estado, new BigDecimal(monto), "MXN", pedido.getFolio());
    }

    private PagoController controlador() {
        return new PagoController(pagoService, stripe, pedidoRepository, usuarioRepository, mock(SesionService.class));
    }

    private static final String EVENTO_PAGADO = """
            {"type":"charge.succeeded","event_date":"2026-11-03T10:00:00-06:00",
             "transaction":{"id":"tr_123","status":"completed","amount":1.0,"method":"store","order_id":"FALSO"}}""";

    @Test
    void cargoPagadoEnTiendaMarcaPagadoConLosDatosDeOpenpay() {
        Pedido pedido = iniciarConPaynet();
        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(cargo(pedido, "completed", "849.00")));

        // El monto y el order_id del cuerpo son falsos a propósito: solo cuenta lo que responde Openpay
        assertEquals(200, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());

        assertEquals(Pedido.PAGADO, pedido.getEstado());
        assertEquals("tr_123", pedido.getPagoId());
        assertNotNull(pedido.getPagadoEn());
        assertEquals("standard", usuario.getSuscripcion().getTipo());
        verify(correoService, times(1)).enviarTicket(pedido);
        verify(notificacionService, times(1)).avisarCompra(pedido);
        verify(payPal, never()).capturar(any());
    }

    @Test
    void cargoDeOpenpayConMontoOMonedaDistintaNoSeMarcaPagado() {
        Pedido pedido = iniciarConPaynet();
        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(cargo(pedido, "completed", "1.00")));
        assertEquals(200, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());
        assertEquals(Pedido.PENDIENTE, pedido.getEstado());

        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(
                new OpenpayService.Cargo("tr_123", "completed", new BigDecimal("849.00"), "USD", pedido.getFolio())));
        assertEquals(200, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());

        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
        assertNull(pedido.getPagoId());
        assertNull(usuario.getSuscripcion());
        verify(correoService, never()).enviarTicket(any());
        verify(notificacionService, never()).avisarCompra(any());
    }

    @Test
    void eventoDeOpenpayRepetidoNoProcesaDosVeces() {
        Pedido pedido = iniciarConPaynet();
        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(cargo(pedido, "completed", "849.00")));

        assertEquals(200, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());
        assertEquals(200, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());

        assertEquals(Pedido.PAGADO, pedido.getEstado());
        verify(correoService, times(1)).enviarTicket(pedido);
        verify(notificacionService, times(1)).avisarCompra(pedido);
    }

    @Test
    void eventoDeOpenpayEntregadoDosVecesALaVezNoProcesaDosVeces() throws Exception {
        Pedido pedido = iniciarConPaynet();
        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(cargo(pedido, "completed", "849.00")));
        // Cada lectura da una copia, como Mongo. Si las dos entregas pudieran leer a la vez, ambas verían PENDIENTE
        java.util.concurrent.CountDownLatch ambas = new java.util.concurrent.CountDownLatch(2);
        when(pedidoRepository.findByFolio(pedido.getFolio())).thenAnswer(inv -> {
            Pedido guardado = pedidos.get(pedido.getId());
            Pedido copia = new Pedido(guardado.getFolio(), guardado.getUsuarioId(), guardado.getLineas(), guardado.getTotal(),
                    guardado.getEstado(), guardado.getFecha());
            copia.setId(guardado.getId());
            copia.setOrdenId(guardado.getOrdenId());
            copia.setCorreoCliente(guardado.getCorreoCliente());
            ambas.countDown();
            ambas.await(300, java.util.concurrent.TimeUnit.MILLISECONDS);
            return Optional.of(copia);
        });

        Thread otra = new Thread(() -> pagoService.confirmarDesdeOpenpay("tr_123"));
        otra.start();
        pagoService.confirmarDesdeOpenpay("tr_123");
        otra.join();

        verify(correoService, times(1)).enviarTicket(any());
        verify(notificacionService, times(1)).avisarCompra(any());
    }

    @Test
    void cargoDeUnPedidoInexistenteSeIgnora() {
        iniciarConPaynet();
        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(
                new OpenpayService.Cargo("tr_123", "completed", new BigDecimal("849.00"), "MXN", "FG-NO-EXISTE")));
        assertEquals(200, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());

        // Un cargo que Openpay no conoce (evento inventado) tampoco se reintenta
        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.empty());
        assertEquals(200, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());

        assertTrue(pedidos.values().stream().allMatch(p -> Pedido.PENDIENTE.equals(p.getEstado())));
        verify(correoService, never()).enviarTicket(any());
    }

    @Test
    void cargoDeOtroPedidoOSinPagarNoMarcaPagado() {
        Pedido pedido = iniciarConPaynet();
        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(
                new OpenpayService.Cargo("tr_otro", "completed", new BigDecimal("849.00"), "MXN", pedido.getFolio())));
        pagoService.confirmarDesdeOpenpay("tr_123");
        assertEquals(Pedido.PENDIENTE, pedido.getEstado());

        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(cargo(pedido, "in_progress", "849.00")));
        pagoService.confirmarDesdeOpenpay("tr_123");
        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
    }

    @Test
    void pedidoCanceladoPagadoEnTiendaNoSeReactiva() {
        Pedido pedido = iniciarConPaynet();
        pagoService.cancelar(pedido);
        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(cargo(pedido, "completed", "849.00")));

        assertEquals(200, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());

        assertEquals(Pedido.CANCELADO, pedido.getEstado());
        verify(correoService, never()).enviarTicket(any());
    }

    @Test
    void siOpenpayNoRespondeSePideReintentoYElPedidoSigueSinPagar() {
        Pedido pedido = iniciarConPaynet();
        when(openpay.obtenerCargo("tr_123")).thenThrow(new OpenpayService.OpenpayException("sin conexión", null));

        assertEquals(503, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());

        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
    }

    @Test
    void confirmarUnPedidoPaynetConsultaOpenpayYNuncaCapturaEnPayPal() {
        Pedido pedido = iniciarConPaynet();
        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(cargo(pedido, "in_progress", "849.00")));

        pagoService.confirmar(pedido, "tr_123");
        assertEquals(Pedido.PENDIENTE, pedido.getEstado());

        when(openpay.obtenerCargo("tr_123")).thenReturn(Optional.of(cargo(pedido, "completed", "849.00")));
        pagoService.confirmar(pedido, "tr_123");

        assertEquals(Pedido.PAGADO, pedido.getEstado());
        verify(payPal, never()).capturar(any());
    }

    @Test
    void verificacionDelWebhookDeOpenpayRespondeOkYNoConsultaCargos() {
        paynetConfigurado();

        assertEquals(200, controlador().webhookOpenpay(
                "{\"type\":\"verification\",\"event_date\":\"2026-11-03T10:00:00-06:00\",\"verification_code\":\"UY1qqrxw\"}")
                .getStatusCode().value());
        assertEquals(200, controlador().webhookOpenpay("{\"type\":\"charge.refunded\",\"transaction\":{\"id\":\"tr_123\"}}").getStatusCode().value());
        assertEquals(200, controlador().webhookOpenpay("no es json").getStatusCode().value());
        assertEquals(200, controlador().webhookOpenpay(null).getStatusCode().value());

        verify(openpay, never()).obtenerCargo(any());
    }

    @Test
    void sinOpenpayConfiguradoElWebhookNoExiste() {
        when(openpay.estaConfigurado()).thenReturn(false);

        assertEquals(404, controlador().webhookOpenpay(EVENTO_PAGADO).getStatusCode().value());
    }

    @Test
    void leeElCargoConElFormatoDeOpenpay() throws Exception {
        OpenpayService.Cargo leido = OpenpayService.Cargo.de(new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"id":"trzjaozcik8msyqshka4","amount":849.00,"status":"completed","currency":"MXN","order_id":"FG-1","method":"store"}"""));

        assertEquals("trzjaozcik8msyqshka4", leido.id());
        assertEquals("completed", leido.estado());
        assertEquals(0, new BigDecimal("849").compareTo(leido.monto()));
        assertEquals("MXN", leido.moneda());
        assertEquals("FG-1", leido.folio());
    }
}
