package com.fluxguard;

import com.fluxguard.controller.AuthController;
import com.fluxguard.controller.PagoController;
import com.fluxguard.controller.ProspectoController;
import com.fluxguard.dto.LoginRequest;
import com.fluxguard.dto.RegisterRequest;
import com.fluxguard.model.Pedido;
import com.fluxguard.model.Sesion;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.ProspectoRepository;
import com.fluxguard.repository.SesionRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.BrevoEmailService;
import com.fluxguard.service.CorreoService;
import com.fluxguard.service.PagoService;
import com.fluxguard.service.ResendEmailService;
import com.fluxguard.service.SesionService;
import com.fluxguard.service.StripeService;
import com.fluxguard.service.Tokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControladoresTests {

    private UsuarioRepository usuarioRepository;
    private SesionRepository sesionRepository;
    private SesionService sesionService;
    private AuthController authController;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        sesionRepository = mock(SesionRepository.class);
        when(sesionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        sesionService = new SesionService(sesionRepository, usuarioRepository);
        authController = new AuthController(usuarioRepository, encoder,
                new CorreoService(new ResendEmailService("", "", "FluxGuard"), new BrevoEmailService("", "", "FluxGuard")), sesionService, "http://localhost:8080");
    }

    private Usuario usuarioVerificado() {
        Usuario usuario = new Usuario("Ana", "Lopez", "ana@fluxguard.io", encoder.encode("Segura#123"));
        usuario.setId("u1");
        usuario.setVerificado(true);
        return usuario;
    }

    private LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void loginNoRevelaSiElCorreoExiste() {
        when(usuarioRepository.findByCorreo("ana@fluxguard.io")).thenReturn(Optional.of(usuarioVerificado()));
        when(usuarioRepository.findByCorreo("nadie@fluxguard.io")).thenReturn(Optional.empty());

        var sinCuenta = authController.login(login("nadie@fluxguard.io", "Segura#123"));
        var malaClave = authController.login(login("ana@fluxguard.io", "Otra#1234"));

        assertEquals(HttpStatus.UNAUTHORIZED, sinCuenta.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, malaClave.getStatusCode());
        assertEquals(sinCuenta.getBody().getMessage(), malaClave.getBody().getMessage());
    }

    @Test
    void loginCorrectoEntregaTokenDeSesion() {
        when(usuarioRepository.findByCorreo("ana@fluxguard.io")).thenReturn(Optional.of(usuarioVerificado()));

        var respuesta = authController.login(login("ana@fluxguard.io", "Segura#123"));

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody().getToken());
        verify(sesionRepository).save(argThat(s -> s.getTokenHash().equals(Tokens.sha256(respuesta.getBody().getToken()))));
    }

    @Test
    void sesionExpiradaOMalFormadaNoEsValida() {
        String token = Tokens.nuevo();
        Sesion expirada = new Sesion(Tokens.sha256(token), "u1", LocalDateTime.now().minusDays(8), LocalDateTime.now().minusDays(1));
        when(sesionRepository.findByTokenHash(Tokens.sha256(token))).thenReturn(Optional.of(expirada));

        assertTrue(sesionService.usuarioDesde("Bearer " + token).isEmpty());
        assertTrue(sesionService.usuarioDesde(token).isEmpty());
        assertTrue(sesionService.usuarioDesde(null).isEmpty());
    }

    private RegisterRequest registro(Boolean aceptaPrivacidad) {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Ana");
        request.setApellido("Lopez");
        request.setEmail("ana@fluxguard.io");
        request.setPassword("Segura#123");
        request.setAceptaPrivacidad(aceptaPrivacidad);
        return request;
    }

    @Test
    void registroExigeAceptarElAvisoDePrivacidad() {
        assertEquals(HttpStatus.BAD_REQUEST, authController.register(registro(null)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, authController.register(registro(false)).getStatusCode());
        verify(usuarioRepository, never()).save(any());

        var respuesta = authController.register(registro(true));

        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        verify(usuarioRepository).save(argThat(u -> u.getPrivacidadAceptadaEn() != null
                && AuthController.VERSION_AVISO_PRIVACIDAD.equals(u.getVersionAvisoPrivacidad())));
    }

    @Test
    void historialDeComprasSoloConSesionYSoloLosPedidosPropios() {
        Usuario usuario = usuarioVerificado();
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario));
        String token = sesionService.crear(usuario);
        when(sesionRepository.findByTokenHash(Tokens.sha256(token))).thenReturn(Optional.of(
                new Sesion(Tokens.sha256(token), "u1", LocalDateTime.now(), LocalDateTime.now().plusDays(1))));

        PedidoRepository pedidoRepository = mock(PedidoRepository.class);
        Pedido propio = new Pedido("FLX-P1", "u1", List.of(), 350, Pedido.PAGADO, LocalDateTime.now());
        when(pedidoRepository.findByUsuarioIdOrderByFechaDesc("u1")).thenReturn(List.of(propio));
        PagoController controller = new PagoController(mock(PagoService.class), mock(StripeService.class),
                pedidoRepository, usuarioRepository, sesionService);

        assertEquals(HttpStatus.UNAUTHORIZED, controller.pedidos(null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, controller.pedidos("Bearer " + Tokens.nuevo()).getStatusCode());

        var respuesta = controller.pedidos("Bearer " + token);
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(List.of(propio), respuesta.getBody().getPedidos());
        verify(pedidoRepository, only()).findByUsuarioIdOrderByFechaDesc("u1");
    }

    @Test
    void ticketPdfSoloParaElDuenoYConElPedidoPagado() {
        Usuario usuario = usuarioVerificado();
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario));
        String token = sesionService.crear(usuario);
        when(sesionRepository.findByTokenHash(Tokens.sha256(token))).thenReturn(Optional.of(
                new Sesion(Tokens.sha256(token), "u1", LocalDateTime.now(), LocalDateTime.now().plusDays(1))));

        PedidoRepository pedidoRepository = mock(PedidoRepository.class);
        List<Pedido.Linea> lineas = List.of(new Pedido.Linea("kit-piloto", "Kit Piloto FluxGuard", 350, 1, 350));
        when(pedidoRepository.findByFolio("FLX-PAGADO")).thenReturn(Optional.of(new Pedido("FLX-PAGADO", "u1", lineas, 350, Pedido.PAGADO, LocalDateTime.now())));
        when(pedidoRepository.findByFolio("FLX-PENDIENTE")).thenReturn(Optional.of(new Pedido("FLX-PENDIENTE", "u1", lineas, 350, Pedido.PENDIENTE, LocalDateTime.now())));
        when(pedidoRepository.findByFolio("FLX-AJENO")).thenReturn(Optional.of(new Pedido("FLX-AJENO", "otro", lineas, 350, Pedido.PAGADO, LocalDateTime.now())));
        PagoController controller = new PagoController(mock(PagoService.class), mock(StripeService.class),
                pedidoRepository, usuarioRepository, sesionService);
        String auth = "Bearer " + token;

        assertEquals(HttpStatus.UNAUTHORIZED, controller.ticket(null, null, "FLX-PAGADO").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.ticket(auth, null, "FLX-AJENO").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, controller.ticket(auth, null, "FLX-PENDIENTE").getStatusCode());

        var respuesta = controller.ticket(auth, null, "FLX-PAGADO");
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals("application/pdf", respuesta.getHeaders().getContentType().toString());
        assertTrue(respuesta.getHeaders().getContentDisposition().getFilename().equals("ticket-FLX-PAGADO.pdf"));
        assertEquals("%PDF-", new String((byte[]) respuesta.getBody(), 0, 5));
    }

    @Test
    void listaDeProspectosRequiereClaveDeAdministrador() {
        ProspectoRepository repo = mock(ProspectoRepository.class);
        when(repo.findAllByOrderByFechaRegistroDesc()).thenReturn(List.of());

        assertEquals(HttpStatus.FORBIDDEN, new ProspectoController(repo, mock(com.fluxguard.service.CorreoService.class), new com.fluxguard.service.LimiteSolicitudes(), "", "").obtenerProspectos("cualquiera").getStatusCode());
        ProspectoController controller = new ProspectoController(repo, mock(com.fluxguard.service.CorreoService.class), new com.fluxguard.service.LimiteSolicitudes(), "clave-secreta", "");
        assertEquals(HttpStatus.FORBIDDEN, controller.obtenerProspectos(null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.obtenerProspectos("otra").getStatusCode());
        assertEquals(HttpStatus.OK, controller.obtenerProspectos("clave-secreta").getStatusCode());
    }
}
