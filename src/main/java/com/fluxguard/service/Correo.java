package com.fluxguard.service;

/**
 * Un correo listo para enviar con cualquier proveedor (Resend o Brevo).
 *
 * @param para       dirección del destinatario
 * @param nombrePara nombre del destinatario (puede ser null)
 * @param responderA dirección a la que llegan las respuestas (Reply-To); null = sin Reply-To
 * @param adjunto    archivo adjunto, p. ej. el PDF del ticket; null = sin adjunto
 */
public record Correo(String para, String nombrePara, String asunto, String html, String texto, String responderA,
                     Adjunto adjunto) {

    /** Correo sin adjunto. */
    public Correo(String para, String nombrePara, String asunto, String html, String texto, String responderA) {
        this(para, nombrePara, asunto, html, texto, responderA, null);
    }
}
