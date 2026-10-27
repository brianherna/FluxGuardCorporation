package com.fluxguard;

import com.fluxguard.controller.AuthController;
import com.fluxguard.controller.ProspectoController;
import com.fluxguard.dto.LoginRequest;
import com.fluxguard.model.Sesion;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.ProspectoRepository;
import com.fluxguard.repository.SesionRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.BrevoEmailService;
import com.fluxguard.service.CorreoService;
import com.fluxguard.service.ResendEmailService;
import com.fluxguard.service.SesionService;
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
