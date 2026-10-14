package com.fluxguard.service;

import com.fluxguard.model.Usuario;

/**
 * Quien usa el carrito: un usuario con sesión o un invitado.
 * El invitado se identifica con un token aleatorio que guarda su navegador (encabezado X-Carrito-Invitado);
 * en la base solo se guarda su hash, como "invitado:<sha256>", en el campo usuarioId de carritos y pedidos.
 *
 * @param clave   id con el que se guardan su carrito y sus pedidos
 * @param usuario el usuario de la sesión, o null si es invitado
 */
public record Comprador(String clave, Usuario usuario) {

    private static final String PREFIJO_INVITADO = "invitado:";

    public static Comprador de(Usuario usuario) {
        return new Comprador(usuario.getId(), usuario);
    }

    public static Comprador invitado(String token) {
        return new Comprador(PREFIJO_INVITADO + Tokens.sha256(token), null);
    }

    public boolean esInvitado() {
        return usuario == null;
    }
}
