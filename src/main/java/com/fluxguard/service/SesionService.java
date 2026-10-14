package com.fluxguard.service;

import com.fluxguard.model.Sesion;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.SesionRepository;
import com.fluxguard.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Sesiones con token opaco: el login entrega un token que el navegador envía en
 * "Authorization: Bearer <token>". Solo se guarda su hash en la colección "sesiones".
 */
@Service
public class SesionService {

    private static final int DIAS_VIGENCIA = 7;
    private static final String PREFIJO = "Bearer ";
    private static final Pattern TOKEN_INVITADO = Pattern.compile("^[A-Za-z0-9_-]{20,100}$");

    private final SesionRepository sesionRepository;
    private final UsuarioRepository usuarioRepository;

    public SesionService(SesionRepository sesionRepository, UsuarioRepository usuarioRepository) {
        this.sesionRepository = sesionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Crea una sesión para el usuario y devuelve el token en claro (solo se muestra una vez). */
    public String crear(Usuario usuario) {
        String token = Tokens.nuevo();
        LocalDateTime ahora = LocalDateTime.now();
        sesionRepository.save(new Sesion(Tokens.sha256(token), usuario.getId(), ahora, ahora.plusDays(DIAS_VIGENCIA)));
        return token;
    }

    /** Devuelve el usuario dueño del token del encabezado Authorization, si la sesión sigue vigente. */
    public Optional<Usuario> usuarioDesde(String authorization) {
        String token = extraerToken(authorization);
        if (token == null) {
            return Optional.empty();
        }
        return sesionRepository.findByTokenHash(Tokens.sha256(token))
                .filter(sesion -> sesion.getExpira() != null && LocalDateTime.now().isBefore(sesion.getExpira()))
                .flatMap(sesion -> usuarioRepository.findById(sesion.getUsuarioId()));
    }

    /**
     * Quién está usando el carrito. Con encabezado Authorization se exige una sesión válida (si venció, vacío,
     * para que el navegador sepa que debe olvidarla); sin él, se usa el token de invitado del navegador.
     */
    public Optional<Comprador> compradorDesde(String authorization, String tokenInvitado) {
        if (authorization != null && !authorization.isBlank()) {
            return usuarioDesde(authorization).map(Comprador::de);
        }
        if (tokenInvitado != null && TOKEN_INVITADO.matcher(tokenInvitado).matches()) {
            return Optional.of(Comprador.invitado(tokenInvitado));
        }
        return Optional.empty();
    }

    public void cerrar(String authorization) {
        String token = extraerToken(authorization);
        if (token != null) {
            sesionRepository.deleteByTokenHash(Tokens.sha256(token));
        }
    }

    private static String extraerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(PREFIJO)) {
            return null;
        }
        String token = authorization.substring(PREFIJO.length()).trim();
        return token.isEmpty() || token.length() > 100 ? null : token;
    }
}
