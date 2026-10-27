package com.fluxguard.service;

import com.fluxguard.model.Pedido;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Ticket de compra que se envía por correo al confirmarse el pago. Lo comparten Brevo y Resend.
 * Los precios del catálogo ya incluyen IVA: el ticket solo desglosa cuánto de ese total es IVA.
 */
public final class PlantillaTicket {

    public static final String CORREO_CONTACTO = "fluxguardcorporation@gmail.com";
    public static final String DOMICILIO = "Paseo del Agrarismo 2000, Carr. Mixquiahuala–Tula km 2.5, Mixquiahuala de Juárez, Hidalgo, México";
    private static final BigDecimal TASA_IVA = new BigDecimal("1.16");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "MX"));
    private static final Map<String, String> METODOS = Map.of(
            "paypal_sandbox", "PayPal (modo de prueba)", "paypal", "PayPal",
            "stripe_test", "Tarjeta (modo de prueba)", "stripe", "Tarjeta",
            "sin_costo", "Sin costo");

    private PlantillaTicket() {}

    public static String asunto(Pedido pedido) {
        return "Tu ticket de compra FluxGuard · " + pedido.getFolio();
    }

    /** Parte del total que corresponde al IVA (16 %), con dos decimales. */
    public static BigDecimal iva(int total) {
        BigDecimal conIva = BigDecimal.valueOf(total);
        return conIva.subtract(conIva.divide(TASA_IVA, 2, RoundingMode.HALF_UP));
    }

    public static String metodo(Pedido pedido) {
        // Map.of no acepta claves null: los pedidos antiguos pueden no tener método guardado
        return pedido.getMetodoPago() == null ? "Sin costo" : METODOS.getOrDefault(pedido.getMetodoPago(), "Sin costo");
    }

    static String fecha(Pedido pedido) {
        LocalDateTime fecha = pedido.getPagadoEn() != null ? pedido.getPagadoEn() : pedido.getFecha();
        return fecha != null ? fecha.format(FECHA) : "";
    }

    static String dinero(BigDecimal monto) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
        return formato.format(monto);
    }

    static String dinero(int monto) {
        return dinero(BigDecimal.valueOf(monto));
    }

    public static String texto(Pedido pedido) {
        StringBuilder texto = new StringBuilder()
                .append("FLUXGUARD · TICKET DE COMPRA\n\n")
                .append("Folio: ").append(pedido.getFolio()).append('\n')
                .append("Fecha: ").append(fecha(pedido)).append('\n')
                .append("Cliente: ").append(pedido.getNombreCliente() != null ? pedido.getNombreCliente() : "").append('\n')
                .append("Método de pago: ").append(metodo(pedido)).append("\n\n");
        for (Pedido.Linea linea : pedido.getLineas()) {
            texto.append(linea.cantidad()).append(" x ").append(linea.nombre())
                    .append(" (").append(dinero(linea.precioUnitario())).append(" c/u) = ").append(dinero(linea.importe())).append('\n');
        }
        BigDecimal iva = iva(pedido.getTotal());
        return texto.append('\n')
                .append("Subtotal: ").append(dinero(BigDecimal.valueOf(pedido.getTotal()).subtract(iva))).append('\n')
                .append("IVA (16 %): ").append(dinero(iva)).append('\n')
                .append("TOTAL: ").append(dinero(pedido.getTotal())).append(" MXN\n\n")
                .append("Este ticket no es un comprobante fiscal (CFDI). Si necesitas factura, escríbenos a ")
                .append(CORREO_CONTACTO).append(" con tu folio.\n")
                .append("Conserva tu folio para garantías y devoluciones.\n\n")
                .append("FluxGuard · ").append(DOMICILIO)
                .toString();
    }

    public static String html(Pedido pedido) {
        StringBuilder filas = new StringBuilder();
        for (Pedido.Linea linea : pedido.getLineas()) {
            filas.append("""
                    <tr>
                      <td style="padding:10px 0;border-bottom:1px solid #2c2342;color:#ffffff;font-size:14px;">%s<br><span style="color:#9a90b5;font-size:12px;">%d × %s</span></td>
                      <td align="right" style="padding:10px 0;border-bottom:1px solid #2c2342;color:#ffffff;font-size:14px;font-weight:bold;white-space:nowrap;">%s</td>
                    </tr>
                    """.formatted(e(linea.nombre()), linea.cantidad(), e(dinero(linea.precioUnitario())), e(dinero(linea.importe()))));
        }
        BigDecimal iva = iva(pedido.getTotal());
        String cliente = pedido.getNombreCliente() != null && !pedido.getNombreCliente().isBlank()
                ? e(pedido.getNombreCliente()) : e(pedido.getCorreoCliente());

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#0b0812;font-family:Arial,Helvetica,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0b0812;padding:32px 12px;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0" style="max-width:560px;width:100%%;background:#120c1e;border:1px solid #2c2342;">
                        <tr><td style="padding:28px 32px 8px;color:#b9a7ff;font-size:12px;font-weight:bold;letter-spacing:3px;">FLUXGUARD / TICKET DE COMPRA</td></tr>
                        <tr><td style="padding:8px 32px;color:#ffffff;font-size:26px;font-weight:bold;">¡Gracias por tu compra!</td></tr>
                        <tr><td style="padding:4px 32px 20px;color:#dfd8f0;font-size:14px;line-height:1.6;">
                          Hola %s, tu pago fue confirmado. Este es el ticket de tu pedido (también va adjunto en PDF); consérvalo para garantías y devoluciones.
                        </td></tr>
                        <tr><td style="padding:0 32px 16px;">
                          <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0e0a17;border:1px solid #2c2342;">
                            <tr>
                              <td style="padding:14px 16px;color:#9a90b5;font-size:11px;letter-spacing:1px;">FOLIO<br><span style="color:#ffffff;font-size:15px;font-family:monospace;letter-spacing:0;">%s</span></td>
                              <td style="padding:14px 16px;color:#9a90b5;font-size:11px;letter-spacing:1px;">FECHA<br><span style="color:#ffffff;font-size:14px;letter-spacing:0;">%s</span></td>
                              <td style="padding:14px 16px;color:#9a90b5;font-size:11px;letter-spacing:1px;">PAGO<br><span style="color:#ffffff;font-size:14px;letter-spacing:0;">%s</span></td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:0 32px;">
                          <table width="100%%" cellpadding="0" cellspacing="0">
                            %s
                            <tr><td style="padding:12px 0 4px;color:#9a90b5;font-size:13px;">Subtotal</td><td align="right" style="padding:12px 0 4px;color:#dfd8f0;font-size:13px;">%s</td></tr>
                            <tr><td style="padding:4px 0;color:#9a90b5;font-size:13px;">IVA (16 %%)</td><td align="right" style="padding:4px 0;color:#dfd8f0;font-size:13px;">%s</td></tr>
                            <tr><td style="padding:12px 0 24px;color:#b9a7ff;font-size:13px;font-weight:bold;letter-spacing:2px;">TOTAL</td><td align="right" style="padding:12px 0 24px;color:#ffffff;font-size:26px;font-weight:bold;">%s</td></tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px;color:#9a90b5;font-size:12px;line-height:1.6;border-top:1px solid #2c2342;">
                          <br>Este ticket no es un comprobante fiscal (CFDI). Si necesitas factura, escríbenos a
                          <a href="mailto:%s" style="color:#b9a7ff;">%s</a> con tu folio.<br><br>
                          FluxGuard · %s
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(cliente, e(pedido.getFolio()), e(fecha(pedido)), e(metodo(pedido)), filas,
                e(dinero(BigDecimal.valueOf(pedido.getTotal()).subtract(iva))), e(dinero(iva)), e(dinero(pedido.getTotal())),
                CORREO_CONTACTO, CORREO_CONTACTO, e(DOMICILIO));
    }

    private static String e(String valor) {
        return HtmlUtils.htmlEscape(valor != null ? valor : "");
    }
}
