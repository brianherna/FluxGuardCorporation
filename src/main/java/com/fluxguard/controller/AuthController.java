package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.LoginRequest;
import com.fluxguard.dto.RegisterRequest;
import com.fluxguard.dto.UsuarioDto;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
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

        if (password.length() < 8 || !tieneMayuscula(password) || !tieneCaracterEspecial(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("La contraseña debe tener al menos 8 caracteres, una letra mayúscula y un carácter especial (ej. @, #, $, !, %)."));
        }

        if (usuarioRepository.existsByCorreo(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Ya existe una cuenta registrada con este correo electrónico. Por favor inicia sesión."));
        }

        String passwordHash = passwordEncoder.encode(password);
        Usuario nuevoUsuario = new Usuario(nombre, apellido, email, passwordHash);
        Usuario guardado = usuarioRepository.save(nuevoUsuario);

        UsuarioDto usuarioDto = new UsuarioDto(
                guardado.getId(),
                guardado.getNombre(),
                guardado.getApellido(),
                guardado.getCorreo(),
                guardado.getFechaRegistro()
        );

        ApiResponse<UsuarioDto> response = ApiResponse.ok("Usuario registrado correctamente en MongoDB Atlas");
        response.setUsuario(usuarioDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

        UsuarioDto usuarioDto = new UsuarioDto(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.getFechaRegistro()
        );

        ApiResponse<UsuarioDto> response = ApiResponse.ok("Inicio de sesión correcto");
        response.setUsuario(usuarioDto);
        return ResponseEntity.ok(response);
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
