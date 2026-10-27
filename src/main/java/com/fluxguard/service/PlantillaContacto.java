package com.fluxguard.service;

import com.fluxguard.model.Prospecto;
import org.springframework.web.util.HtmlUtils;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Correos del formulario de contacto: la confirmación para el cliente y el aviso para el equipo.
 * Todo lo que escribió el cliente se escapa antes de ponerlo en el HTML.
 */
public final class PlantillaContacto {

    private static final Map<String, String> INTERESES = Map.of(
            "Prototipo", "Solicitud de Prototipo Piloto",
            "Monitoreo", "Monitoreo de redes eléctricas",
            "Analisis", "Análisis y exportación de datos",
            "Alertas", "Detección de anomalías en tiempo real",
            "Plataforma", "Demostración de la plataforma web",
            "Implementacion", "Implementación a escala empresarial",
            "Otro", "Otro requerimiento");

    private static final Map<String, String> SECTORES = Map.of(
            "Energia", "Energía",
            "Industria", "Industria y Manufactura",
            "Tecnologia", "Tecnología & IoT",
            "Gobierno", "Gobierno / Infraestructura Pública",
            "Educacion", "Educación / Investigación",
            "Otro", "Otro");

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private PlantillaContacto() {}

    public static String interes(String valor) {
        return valor == null || valor.isBlank() ? "Sin especificar" : INTERESES.getOrDefault(valor, valor);
    }

    public static String sector(String valor) {
        return valor == null || valor.isBlank() ? "Sin especificar" : SECTORES.getOrDefault(valor, valor);
    }

    /** "Recibimos tu solicitud" para el cliente. Sus respuestas llegan al correo del equipo. */
    public static Correo confirmacionCliente(Prospecto p, String correoEquipo, int horasRespuesta) {
        String asunto = "Recibimos tu solicitud " + p.getFolio() + " · FluxGuard";
        String texto = "Hola " + p.getNombre() + ",\n\n"
                + "Gracias por contactar a FluxGuard. Recibimos tu solicitud y ya la estamos atendiendo.\n"
                + "Un especialista se pondrá en contacto contigo en un máximo de " + horasRespuesta + " horas hábiles.\n\n"
                + "Folio: " + p.getFolio() + "\n"
                + "Interés: " + interes(p.getInteres()) + "\n"
                + (vacio(p.getEmpresa()) ? "" : "Empresa: " + p.getEmpresa() + "\n")
                + "Teléfono de contacto: " + p.getTelefono() + "\n\n"
                + "Si quieres agregar algo, solo responde este correo.\n\n"
                + "Equipo FluxGuard";

        String filas = fila("Folio", p.getFolio())
                + fila("Interés", interes(p.getInteres()))
                + (vacio(p.getEmpresa()) ? "" : fila("Empresa", p.getEmpresa()))
                + fila("Teléfono", p.getTelefono());

        String cuerpo = """
                <tr><td style="padding:8px 32px;color:#ffffff;font-size:26px;font-weight:bold;">Estamos atendiendo tu solicitud</td></tr>
                <tr><td style="padding:8px 32px 18px;color:#dfd8f0;font-size:15px;line-height:1.6;">
                  Hola %s, gracias por contactar a FluxGuard. Recibimos tu solicitud y un especialista se pondrá en contacto
                  contigo en un máximo de <strong style="color:#ffffff;">%d horas hábiles</strong>.
                </td></tr>
                <tr><td style="padding:0 32px 20px;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #2c2342;">%s</table>
                </td></tr>
                <tr><td style="padding:0 32px 28px;color:#9a90b5;font-size:12px;line-height:1.6;">
                  ¿Quieres agregar algo? Solo responde este correo. Guarda tu folio para cualquier seguimiento.
                </td></tr>
                """.formatted(esc(p.getNombre()), horasRespuesta, filas);

        return new Correo(p.getCorreo(), p.getNombre() + " " + p.getApellido(), asunto,
                marco("FLUXGUARD / CONTACTO", cuerpo), texto, correoEquipo);
    }

    /** Aviso interno con todos los datos del prospecto. "Responder" le contesta directo al cliente. */
    public static Correo avisoEquipo(Prospecto p, String correoEquipo) {
        String nombreCompleto = p.getNombre() + " " + p.getApellido();
        String asunto = "Nueva solicitud " + p.getFolio() + ": " + nombreCompleto + " · " + interes(p.getInteres());
        String fecha = p.getFechaRegistro() != null ? p.getFechaRegistro().format(FECHA) : "";
        String mensaje = vacio(p.getMensaje()) ? "(sin mensaje)" : p.getMensaje();

        String texto = "Nueva solicitud desde el formulario de contacto.\n\n"
                + "Folio: " + p.getFolio() + "\nFecha: " + fecha + "\n"
                + "Nombre: " + nombreCompleto + "\nCorreo: " + p.getCorreo() + "\nTeléfono: " + p.getTelefono() + "\n"
                + "Empresa: " + texto(p.getEmpresa()) + "\nCargo: " + texto(p.getCargo()) + "\n"
                + "Sector: " + sector(p.getSector()) + "\nInterés: " + interes(p.getInteres()) + "\n\n"
                + "Mensaje:\n" + mensaje + "\n\n"
                + "Responde este correo para contestarle directamente al cliente.";

        String filas = fila("Folio", p.getFolio())
                + fila("Fecha", fecha)
                + fila("Nombre", nombreCompleto)
                + fila("Correo", p.getCorreo())
                + fila("Teléfono", p.getTelefono())
                + fila("Empresa", texto(p.getEmpresa()))
                + fila("Cargo", texto(p.getCargo()))
                + fila("Sector", sector(p.getSector()))
                + fila("Interés", interes(p.getInteres()));

        String cuerpo = """
                <tr><td style="padding:8px 32px;color:#ffffff;font-size:24px;font-weight:bold;">Nueva solicitud de contacto</td></tr>
                <tr><td style="padding:8px 32px 18px;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #2c2342;">%s</table>
                </td></tr>
                <tr><td style="padding:0 32px 6px;color:#b9a7ff;font-size:12px;font-weight:bold;letter-spacing:2px;">MENSAJE</td></tr>
                <tr><td style="padding:0 32px 20px;color:#dfd8f0;font-size:14px;line-height:1.6;white-space:pre-wrap;">%s</td></tr>
                <tr><td style="padding:0 32px 28px;color:#9a90b5;font-size:12px;line-height:1.6;">
                  Responde este correo para contestarle directamente a %s.
                </td></tr>
                """.formatted(filas, esc(mensaje), esc(p.getNombre()));

        return new Correo(correoEquipo, "Equipo FluxGuard", asunto, marco("FLUXGUARD / NUEVO PROSPECTO", cuerpo), texto, p.getCorreo());
    }

    private static String marco(String etiqueta, String filas) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#0b0812;font-family:Arial,Helvetica,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0b0812;padding:32px 12px;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0" style="max-width:560px;width:100%%;background:#120c1e;border:1px solid #2c2342;">
                        <tr><td style="padding:28px 32px 8px;color:#b9a7ff;font-size:12px;font-weight:bold;letter-spacing:3px;">%s</td></tr>
                        %s
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(esc(etiqueta), filas);
    }

    private static String fila(String etiqueta, String valor) {
        return "<tr><td style=\"padding:9px 14px;color:#9a90b5;font-size:12px;border-bottom:1px solid #2c2342;width:110px;\">"
                + esc(etiqueta) + "</td><td style=\"padding:9px 14px;color:#ffffff;font-size:13px;border-bottom:1px solid #2c2342;\">"
                + esc(valor) + "</td></tr>";
    }

    private static String esc(String valor) {
        return HtmlUtils.htmlEscape(valor == null ? "" : valor);
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String texto(String valor) {
        return vacio(valor) ? "—" : valor;
    }
}
