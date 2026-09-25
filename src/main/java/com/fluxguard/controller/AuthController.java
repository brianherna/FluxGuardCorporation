package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.LoginRequest;
import com.fluxguard.dto.RegisterRequest;
import com.fluxguard.dto.ReenvioRequest;
import com.fluxguard.dto.UsuarioDto;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.BrevoEmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int HORAS_VIGENCIA_ENLACE = 24;
    private static final Duration ESPERA_REENVIO = Duration.ofSeconds(60);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final BrevoEmailService emailService;
    private final String appBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public AuthController(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          BrevoEmailService emailService,
                          @Value("${app.base-url:${APP_BASE_URL:http://localhost:8080}}") String appBaseUrl) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl.trim().replaceAll("/+$", "");
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UsuarioDto>> register(@RequestBody RegisterRequest request) {
        String nombre = request.getNombre() != null ? request.getNombre().trim() : "";
        String apellido = request.getApellido() != null ? request.getApellido().trim() : "";
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";

        if (nombre.isEmpty() || apellido.isEmpty() || email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Todos los campos son obligatorios"));
        }

        if (nombre.length() > 100 || apellido.length() > 100 || email.length() > 150) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Uno de los campos supera la longitud permitida."));
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("El correo electrónico no es válido."));
        }

        if (password.length() < 8 || !tieneMayuscula(password) || !tieneCaracterEspecial(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("La contraseña debe tener al menos 8 caracteres, una letra mayúscula y un carácter especial (ej. @, #, $, !, %)."));
        }

        Optional<Usuario> existente = usuarioRepository.findByCorreo(email);
        Usuario usuario;
        if (existente.isPresent()) {
            usuario = existente.get();
            if (usuario.estaVerificado()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("Ya existe una cuenta registrada con este correo electrónico. Por favor inicia sesión."));
            }
            long espera = segundosParaReenvio(usuario);
            if (espera > 0) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(pendienteDeVerificacion(false, "Ya te enviamos un enlace de verificación. Espera " + espera + " segundos antes de pedir otro.", usuario));
            }
            // Cuenta pendiente de verificación: se actualizan los datos y se envía un enlace nuevo
            usuario.setNombre(nombre);
            usuario.setApellido(apellido);
            usuario.setPasswordHash(passwordEncoder.encode(password));
        } else {
            usuario = new Usuario(nombre, apellido, email, passwordEncoder.encode(password));
        }

        String token = asignarToken(usuario);
        Usuario guardado = usuarioRepository.save(usuario);

        if (!enviarEnlace(guardado, token)) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(pendienteDeVerificacion(false, "Tu cuenta fue creada, pero no pudimos enviar el correo de verificación. Usa \"Reenviar correo\" en unos momentos.", guardado));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pendienteDeVerificacion(true, "Cuenta creada. Te enviamos un enlace de verificación a " + guardado.getCorreo() + ". Ábrelo para activar tu cuenta.", guardado));
    }

    /** Enlace del correo de Brevo: activa la cuenta y redirige a login.html con el resultado. */
    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam(defaultValue = "") String token) {
        String resultado = verificarToken(token.trim());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(appBaseUrl + "/login.html?verificacion=" + resultado))
                .build();
    }

    @PostMapping("/resend")
    public ResponseEntity<ApiResponse<UsuarioDto>> resend(@RequestBody ReenvioRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (email.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("El correo electrónico es obligatorio."));
        }

        Optional<Usuario> optionalUsuario = usuarioRepository.findByCorreo(email);
        if (optionalUsuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No existe ninguna cuenta registrada con este correo electrónico."));
        }

        Usuario usuario = optionalUsuario.get();
        if (usuario.estaVerificado()) {
            return ResponseEntity.ok(ApiResponse.ok("Tu cuenta ya está verificada. Inicia sesión para continuar."));
        }

        long espera = segundosParaReenvio(usuario);
        if (espera > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(pendienteDeVerificacion(false, "Espera " + espera + " segundos antes de solicitar otro enlace.", usuario));
        }

        String token = asignarToken(usuario);
        usuarioRepository.save(usuario);

        if (!enviarEnlace(usuario, token)) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(pendienteDeVerificacion(false, "No pudimos enviar el correo de verificación. Intenta de nuevo en unos momentos.", usuario));
        }

        return ResponseEntity.ok(pendienteDeVerificacion(true, "Te enviamos un enlace nuevo a " + usuario.getCorreo() + ".", usuario));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UsuarioDto>> login(@RequestBody LoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";

        if (email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Correo y contraseña son obligatorios"));
        }

        Optional<Usuario> optionalUsuario = usuarioRepository.findByCorreo(email);
        if (optionalUsuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No existe ninguna cuenta registrada con este correo electrónico. Tienes prohibido el acceso hasta que crees una cuenta."));
        }

        Usuario usuario = optionalUsuario.get();
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Contraseña incorrecta para esta cuenta."));
        }

        if (!usuario.estaVerificado()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(pendienteDeVerificacion(false, "Debes verificar tu correo electrónico antes de iniciar sesión. Revisa tu bandeja de entrada (y la carpeta de spam).", usuario));
        }

        ApiResponse<UsuarioDto> response = ApiResponse.ok("Inicio de sesión correcto");
        response.setUsuario(toDto(usuario));
        return ResponseEntity.ok(response);
    }

    /** Devuelve "ok", "expirado" o "invalido" según el token del enlace. */
    private String verificarToken(String token) {
        if (token.isEmpty() || token.length() > 100) {
            return "invalido";
        }

        Optional<Usuario> optionalUsuario = usuarioRepository.findByTokenVerificacionHash(sha256(token));
        if (optionalUsuario.isEmpty()) {
            return "invalido";
        }

        Usuario usuario = optionalUsuario.get();
        if (usuario.getTokenExpira() == null || LocalDateTime.now().isAfter(usuario.getTokenExpira())) {
            return "expirado";
        }

        usuario.setVerificado(true);
        usuario.setTokenVerificacionHash(null);
        usuario.setTokenExpira(null);
        usuarioRepository.save(usuario);
        return "ok";
    }

    /** Genera un token aleatorio, guarda solo su hash en el usuario y devuelve el token en claro para el enlace. */
    private String asignarToken(Usuario usuario) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime ahora = LocalDateTime.now();
        usuario.setTokenVerificacionHash(sha256(token));
        usuario.setTokenExpira(ahora.plusHours(HORAS_VIGENCIA_ENLACE));
        usuario.setTokenEnviadoEn(ahora);
        return token;
    }

    private boolean enviarEnlace(Usuario usuario, String token) {
        String enlace = appBaseUrl + "/api/auth/verify?token=" + token;
        try {
            emailService.enviarEnlaceVerificacion(usuario.getCorreo(), usuario.getNombre(), enlace, HORAS_VIGENCIA_ENLACE);
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    private long segundosParaReenvio(Usuario usuario) {
        if (usuario.getTokenEnviadoEn() == null) {
            return 0;
        }
        long transcurridos = Duration.between(usuario.getTokenEnviadoEn(), LocalDateTime.now()).getSeconds();
        return Math.max(0, ESPERA_REENVIO.getSeconds() - transcurridos);
    }

    private ApiResponse<UsuarioDto> pendienteDeVerificacion(boolean success, String mensaje, Usuario usuario) {
        ApiResponse<UsuarioDto> response = new ApiResponse<>(success, mensaje);
        response.setRequiereVerificacion(true);
        response.setCorreo(usuario.getCorreo());
        return response;
    }

    private static String sha256(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private UsuarioDto toDto(Usuario usuario) {
        return new UsuarioDto(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.getFechaRegistro()
        );
    }

    private boolean tieneMayuscula(String pwd) {
        for (char c : pwd.toCharArray()) {
            if (Character.isUpperCase(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean tieneCaracterEspecial(String pwd) {
        String especiales = "!@#$%^&*(),.?\":{}|<>_-\\/[]~`+=";
        for (char c : pwd.toCharArray()) {
            if (especiales.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }
}
