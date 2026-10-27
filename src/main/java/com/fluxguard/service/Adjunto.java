package com.fluxguard.service;

import java.util.Base64;

/** Archivo adjunto de un correo (p. ej. el PDF del ticket). Los proveedores lo reciben en base64. */
public record Adjunto(String nombre, byte[] contenido) {

    public String base64() {
        return Base64.getEncoder().encodeToString(contenido);
    }
}
