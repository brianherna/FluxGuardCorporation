package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.CuentaRequest;
import com.fluxguard.dto.UsuarioDto;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.Contrasenas;
import com.fluxguard.service.CorreoService;
import com.fluxguard.service.LimiteSolicitudes;
import com.fluxguard.service.SesionService;
import com.fluxguard.service.Tokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Perfil del usuario con sesión: cambiar nombre, contraseña y correo. Cambiar la contraseña o el correo pide
 * la contraseña actual. El correo nuevo no se usa hasta que el usuario abre el enlace que le llega ahí
 * (AuthController.verify), así un error al escribirlo no lo deja fuera de su cuenta.
 */
@RestController
@RequestMapping("/api/cuenta")
public class CuentaController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int HORAS_VIGENCIA_ENLACE = 24;
    private static final Duration ESPERA_REENVIO = Duration.ofSeconds(60);
    // Intentos con la contraseña actual: frena a quien tenga la sesión abierta y quiera adivinarla
    private static final int MAX_INTENTOS = 5;
    private static final Duration VENTANA_INTENTOS = Duration.ofMinutes(15);
    private static final String CONTRASENA_INCORRECTA = "La contraseña actual no es correcta.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoService correoService;
    private final SesionService sesionService;
    private final LimiteSolicitudes limite;
    private final String appBaseUrl;

    public CuentaController(UsuarioRepository usuarioRepository,
                            PasswordEncoder passwordEncoder,
                            CorreoService correoService,
                            SesionService sesionService,
                            LimiteSolicitudes limite,
                            @Value("${app.base-url:${APP_BASE_URL:http://localhost:8080}}") String appBaseUrl) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.correoService = correoService;
        this.sesionService = sesionService;
        this.limite = limite;
        this.appBaseUrl = appBaseUrl.trim().replaceAll("/+$", "");
    }

    @PutMapping("/datos")
    public ResponseEntity<ApiResponse<UsuarioDto>> datos(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @RequestBody CuentaRequest request) {
        return conUsuario(authorization, usuario -> {
            String nombre = limpiar(request.getNombre());
            String apellido = limpiar(request.getApellido());
            if (nombre.isEmpty() || apellido.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "Escribe tu nombre y tu apellido.");
            }
            if (nombre.length() > 100 || apellido.length() > 100) {
                return error(HttpStatus.BAD_REQUEST, "El nombre o el apellido es demasiado largo.");
            }
            usuario.setNombre(nombre);
            usuario.setApellido(apellido);
            return listo("Guardamos tus datos.", usuarioRepository.save(usuario));
        });
    }

    @PostMapping("/contrasena")
    public ResponseEntity<ApiResponse<UsuarioDto>> contrasena(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @RequestBody CuentaRequest request) {
        return conUsuario(authorization, usuario -> {
            String nueva = request.getContrasenaNueva() == null ? "" : request.getContrasenaNueva();
            Optional<ResponseEntity<ApiResponse<UsuarioDto>>> rechazo = comprobarContrasena(usuario, request.getContrasenaActual());
            if (rechazo.isPresent()) {
                return rechazo.get();
            }
            if (!Contrasenas.esSegura(nueva)) {
                return error(HttpStatus.BAD_REQUEST, Contrasenas.REGLA);
            }
            if (passwordEncoder.matches(nueva, usuario.getPasswordHash())) {
                return error(HttpStatus.BAD_REQUEST, "La contraseña nueva debe ser distinta de la actual.");
            }
            usuario.setPasswordHash(passwordEncoder.encode(nueva));
            usuarioRepository.save(usuario);
            // Si alguien más tenía la sesión abierta con la contraseña anterior, ya no podrá seguir
            sesionService.cerrarOtras(usuario, authorization);
            return listo("Cambiamos tu contraseña. Cerramos tu sesión en los demás dispositivos.", usuario);
        });
    }

    @PostMapping("/correo")
    public ResponseEntity<ApiResponse<UsuarioDto>> correo(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @RequestBody CuentaRequest request) {
        return conUsuario(authorization, usuario -> {
            String correo = limpiar(request.getCorreo()).toLowerCase();
            Optional<ResponseEntity<ApiResponse<UsuarioDto>>> rechazo = comprobarContrasena(usuario, request.getContrasenaActual());
            if (rechazo.isPresent()) {
                return rechazo.get();
            }
            if (correo.length() > 150 || !EMAIL_PATTERN.matcher(correo).matches()) {
                return error(HttpStatus.BAD_REQUEST, "El correo electrónico no es válido.");
            }
            if (correo.equals(usuario.getCorreo())) {
                return error(HttpStatus.BAD_REQUEST, "Ese ya es tu correo.");
            }
            if (usuarioRepository.existsByCorreo(correo)) {
                return error(HttpStatus.CONFLICT, "Ya hay una cuenta con ese correo.");
            }
            long espera = segundosParaReenvio(usuario);
            if (espera > 0) {
                return error(HttpStatus.TOO_MANY_REQUESTS, "Espera " + espera + " segundos antes de pedir otro enlace.");
            }
            String token = Tokens.nuevo();
            LocalDateTime ahora = LocalDateTime.now();
            usuario.setCorreoPendiente(correo);
            usuario.setTokenVerificacionHash(Tokens.sha256(token));
            usuario.setTokenExpira(ahora.plusHours(HORAS_VIGENCIA_ENLACE));
            usuario.setTokenEnviadoEn(ahora);
            usuarioRepository.save(usuario);
            try {
                correoService.enviarCambioDeCorreo(correo, usuario.getNombre(),
                        appBaseUrl + "/api/auth/verify?token=" + token, HORAS_VIGENCIA_ENLACE);
            } catch (RestClientException e) {
                return error(HttpStatus.BAD_GATEWAY, "No pudimos enviar el correo de confirmación. Intenta de nuevo en unos momentos.");
            }
            return listo("Te enviamos un enlace a " + correo + ". Tu correo cambiará cuando lo abras.", usuario);
        });
    }

    private Optional<ResponseEntity<ApiResponse<UsuarioDto>>> comprobarContrasena(Usuario usuario, String actual) {
        if (!limite.permitir("cuenta:" + usuario.getId(), MAX_INTENTOS, VENTANA_INTENTOS)) {
            return Optional.of(error(HttpStatus.TOO_MANY_REQUESTS, "Hiciste muchos intentos. Espera unos minutos y vuelve a intentarlo."));
        }
        if (actual == null || actual.isEmpty() || !passwordEncoder.matches(actual, usuario.getPasswordHash())) {
            return Optional.of(error(HttpStatus.BAD_REQUEST, CONTRASENA_INCORRECTA));
        }
        return Optional.empty();
    }

    private ResponseEntity<ApiResponse<UsuarioDto>> conUsuario(String authorization,
                                                               Function<Usuario, ResponseEntity<ApiResponse<UsuarioDto>>> accion) {
        return sesionService.usuarioDesde(authorization)
                .map(accion)
                .orElseGet(() -> error(HttpStatus.UNAUTHORIZED, "Tu sesión expiró. Inicia sesión de nuevo."));
    }

    private long segundosParaReenvio(Usuario usuario) {
        if (usuario.getTokenEnviadoEn() == null) {
            return 0;
        }
        long transcurridos = Duration.between(usuario.getTokenEnviadoEn(), LocalDateTime.now()).getSeconds();
        return Math.max(0, ESPERA_REENVIO.getSeconds() - transcurridos);
    }

    private static ResponseEntity<ApiResponse<UsuarioDto>> listo(String mensaje, Usuario usuario) {
        ApiResponse<UsuarioDto> response = ApiResponse.ok(mensaje);
        response.setUsuario(UsuarioDto.de(usuario));
        return ResponseEntity.ok(response);
    }

    private static ResponseEntity<ApiResponse<UsuarioDto>> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(ApiResponse.error(mensaje));
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
