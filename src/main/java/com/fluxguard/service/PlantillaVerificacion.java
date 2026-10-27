package com.fluxguard.service;

import org.springframework.web.util.HtmlUtils;

/** Contenido del correo de verificación de cuenta. Lo comparten todos los proveedores de envío (Brevo, Resend). */
public final class PlantillaVerificacion {

    public static final String ASUNTO = "Verifica tu correo en FluxGuard";

    private PlantillaVerificacion() {}

    public static String texto(String nombre, String enlace, int horasVigencia) {
        return "Hola " + nombre + ",\n\nAbre este enlace para activar tu cuenta de FluxGuard:\n"
                + enlace + "\n\nEl enlace vence en " + horasVigencia + " horas."
                + "\n\nSi no creaste esta cuenta, ignora este correo.";
    }

    public static String html(String nombre, String enlace, int horasVigencia) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#0b0812;font-family:Arial,Helvetica,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0b0812;padding:32px 12px;">
                    <tr><td align="center">
                      <table width="520" cellpadding="0" cellspacing="0" style="max-width:520px;width:100%%;background:#120c1e;border:1px solid #2c2342;">
                        <tr><td style="padding:28px 32px 8px;color:#b9a7ff;font-size:12px;font-weight:bold;letter-spacing:3px;">FLUXGUARD / VERIFICACIÓN</td></tr>
                        <tr><td style="padding:8px 32px;color:#ffffff;font-size:26px;font-weight:bold;">Confirma tu correo</td></tr>
                        <tr><td style="padding:8px 32px 20px;color:#dfd8f0;font-size:15px;line-height:1.6;">
                          Hola %s, gracias por crear tu cuenta en FluxGuard. Da clic en el botón para activarla:
                        </td></tr>
                        <tr><td align="center" style="padding:0 32px 24px;">
                          <a href="%s" style="display:inline-block;padding:14px 26px;background:#b9a7ff;color:#0b0812;text-decoration:none;font-weight:bold;font-size:13px;letter-spacing:2px;">VERIFICAR MI CUENTA</a>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px;color:#9a90b5;font-size:12px;line-height:1.6;">
                          El enlace vence en %d horas. Si no creaste esta cuenta, puedes ignorar este correo.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(nombre), HtmlUtils.htmlEscape(enlace), horasVigencia);
    }
}
