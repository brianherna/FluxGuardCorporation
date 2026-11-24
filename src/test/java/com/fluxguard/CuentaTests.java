package com.fluxguard;

import com.fluxguard.controller.AuthController;
import com.fluxguard.controller.CuentaController;
import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.CuentaRequest;
import com.fluxguard.dto.UsuarioDto;
import com.fluxguard.model.Sesion;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.SesionRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.CorreoService;
import com.fluxguard.service.LimiteSolicitudes;
import com.fluxguard.service.SesionService;
import com.fluxguard.service.Tokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CuentaTests {

    private static final String SESION = "Bearer token-de-sesion-de-prueba";
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioRepository usuarioRepository;
    private SesionRepository sesionRepository;
    private CorreoService correoService;
    private CuentaController cuenta;
    private AuthController auth;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario("Ana", "Lopez", "ana@fluxguard.io", encoder.encode("Segura#123"));
        usuario.setId("u1");
        usuario.setVerificado(true);

        usuarioRepository = mock(UsuarioRepository.class);
        sesionRepository = mock(SesionRepository.class);
        correoService = mock(CorreoService.class);
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByCorreo("ocupado@fluxguard.io")).thenReturn(true);
        when(usuarioRepository.findByTokenVerificacionHash(anyString())).thenAnswer(inv ->
                Optional.of(usuario).filter(u -> inv.getArgument(0).equals(u.getTokenVerificacionHash())));
        when(sesionRepository.findByTokenHash(Tokens.sha256("token-de-sesion-de-prueba"))).thenReturn(Optional.of(
                new Sesion(Tokens.sha256("token-de-sesion-de-prueba"), "u1", LocalDateTime.now(), LocalDateTime.now().plusDays(1))));

        SesionService sesionService = new SesionService(sesionRepository, usuarioRepository);
        cuenta = new CuentaController(usuarioRepository, encoder, correoService, sesionService, new LimiteSolicitudes(), "https://fluxguard.test");
        auth = new AuthController(usuarioRepository, encoder, correoService, sesionService, "https://fluxguard.test");
    }

    private static CuentaRequest pedido(String nombre, String apellido, String correo, String actual, String nueva) {
        CuentaRequest request = new CuentaRequest();
        request.setNombre(nombre);
        request.setApellido(apellido);
        request.setCorreo(correo);
        request.setContrasenaActual(actual);
        request.setContrasenaNueva(nueva);
        return request;
    }

    @Test
    void cambiaNombreYApellido() {
        ResponseEntity<ApiResponse<UsuarioDto>> r = cuenta.datos(SESION, pedido("  Ana María ", "López", null, null, null));

        assertEquals(200, r.getStatusCode().value());
        assertEquals("Ana María", usuario.getNombre());
        assertEquals("Ana María", r.getBody().getUsuario().getNombre());
        assertEquals(400, cuenta.datos(SESION, pedido("", "López", null, null, null)).getStatusCode().value());
        assertEquals(401, cuenta.datos(null, pedido("Ana", "López", null, null, null)).getStatusCode().value());
    }

    @Test
    void cambiaLaContrasenaYCierraLasDemasSesiones() {
        ResponseEntity<ApiResponse<UsuarioDto>> r = cuenta.contrasena(SESION, pedido(null, null, null, "Segura#123", "Nueva#4567"));

        assertEquals(200, r.getStatusCode().value());
        assertTrue(encoder.matches("Nueva#4567", usuario.getPasswordHash()));
        verify(sesionRepository).deleteByUsuarioIdAndTokenHashNot("u1", Tokens.sha256("token-de-sesion-de-prueba"));
    }

    @Test
    void laContrasenaNoCambiaSiLaActualEsIncorrectaOLaNuevaEsDebil() {
        String hash = usuario.getPasswordHash();

        assertEquals(400, cuenta.contrasena(SESION, pedido(null, null, null, "Otra#123", "Nueva#4567")).getStatusCode().value());
        assertEquals(400, cuenta.contrasena(SESION, pedido(null, null, null, "Segura#123", "debil")).getStatusCode().value());
        assertEquals(400, cuenta.contrasena(SESION, pedido(null, null, null, "Segura#123", "Segura#123")).getStatusCode().value());

        assertEquals(hash, usuario.getPasswordHash());
        verify(sesionRepository, never()).deleteByUsuarioIdAndTokenHashNot(any(), any());
    }

    @Test
    void elCorreoCambiaSoloAlAbrirElEnlace() {
        ResponseEntity<ApiResponse<UsuarioDto>> r = cuenta.correo(SESION, pedido(null, null, "Nuevo@FluxGuard.io ", "Segura#123", null));

        assertEquals(200, r.getStatusCode().value());
        assertEquals("ana@fluxguard.io", usuario.getCorreo());
        assertEquals("nuevo@fluxguard.io", usuario.getCorreoPendiente());
        assertEquals("nuevo@fluxguard.io", r.getBody().getUsuario().getCorreoPendiente());

        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(correoService).enviarCambioDeCorreo(eq("nuevo@fluxguard.io"), eq("Ana"), enlace.capture(), anyInt());
        String token = enlace.getValue().substring(enlace.getValue().indexOf("token=") + "token=".length());

        ResponseEntity<Void> abierto = auth.verify(token);

        assertTrue(abierto.getHeaders().getLocation().toString().endsWith("verificacion=correo"));
        assertEquals("nuevo@fluxguard.io", usuario.getCorreo());
        assertNull(usuario.getCorreoPendiente());
        assertNull(usuario.getTokenVerificacionHash());
    }

    @Test
    void siOtraCuentaTomaElCorreoAntesDeAbrirElEnlaceSeConservaElAnterior() {
        cuenta.correo(SESION, pedido(null, null, "nuevo@fluxguard.io", "Segura#123", null));
        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(correoService).enviarCambioDeCorreo(any(), any(), enlace.capture(), anyInt());
        when(usuarioRepository.existsByCorreo("nuevo@fluxguard.io")).thenReturn(true);

        ResponseEntity<Void> abierto = auth.verify(enlace.getValue().substring(enlace.getValue().indexOf("token=") + 6));

        assertTrue(abierto.getHeaders().getLocation().toString().endsWith("verificacion=ocupado"));
        assertEquals("ana@fluxguard.io", usuario.getCorreo());
        assertNull(usuario.getCorreoPendiente());
    }

    @Test
    void correosInvalidosOcupadosOSinContrasenaSeRechazan() {
        assertEquals(400, cuenta.correo(SESION, pedido(null, null, "nuevo@fluxguard.io", "Otra#123", null)).getStatusCode().value());
        assertEquals(400, cuenta.correo(SESION, pedido(null, null, "no-es-correo", "Segura#123", null)).getStatusCode().value());
        assertEquals(400, cuenta.correo(SESION, pedido(null, null, "ana@fluxguard.io", "Segura#123", null)).getStatusCode().value());
        assertEquals(409, cuenta.correo(SESION, pedido(null, null, "ocupado@fluxguard.io", "Segura#123", null)).getStatusCode().value());

        assertNull(usuario.getCorreoPendiente());
        verifyNoInteractions(correoService);
    }

    @Test
    void siNoSePuedeEnviarElCorreoSeAvisa() {
        doThrow(new RestClientException("sin conexión")).when(correoService).enviarCambioDeCorreo(any(), any(), any(), anyInt());

        assertEquals(502, cuenta.correo(SESION, pedido(null, null, "nuevo@fluxguard.io", "Segura#123", null)).getStatusCode().value());
    }

    @Test
    void despuesDeCincoIntentosSeEsperaUnosMinutos() {
        for (int i = 0; i < 5; i++) {
            assertEquals(400, cuenta.contrasena(SESION, pedido(null, null, null, "Adivina#" + i, "Nueva#4567")).getStatusCode().value());
        }

        // Ni con la contraseña correcta: hay que esperar
        assertEquals(429, cuenta.contrasena(SESION, pedido(null, null, null, "Segura#123", "Nueva#4567")).getStatusCode().value());
        assertTrue(encoder.matches("Segura#123", usuario.getPasswordHash()));
    }
}
