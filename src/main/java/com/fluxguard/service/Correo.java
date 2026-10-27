package com.fluxguard.service;

/**
 * Un correo listo para enviar con cualquier proveedor (Resend o Brevo).
 *
 * @param para       dirección del destinatario
 * @param nombrePara nombre del destinatario (puede ser null)
 * @param responderA dirección a la que llegan las respuestas (Reply-To); null = sin Reply-To
 */
public record Correo(String para, String nombrePara, String asunto, String html, String texto, String responderA) {}
