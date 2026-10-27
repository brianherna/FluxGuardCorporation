package com.fluxguard.service;

import com.fluxguard.model.Pedido;
import org.springframework.stereotype.Service;

/**
 * Elige con qué proveedor se envían los correos:
 * - Resend, si RESEND_API_KEY está configurada.
 * - Brevo en cualquier otro caso (sin BREVO_API_KEY, el enlace se imprime en la consola del servidor).
 * Para volver a Brevo basta con quitar RESEND_API_KEY del .env o de Render.
 */
@Service
public class CorreoService {

    private final ResendEmailService resend;
    private final BrevoEmailService brevo;

    public CorreoService(ResendEmailService resend, BrevoEmailService brevo) {
        this.resend = resend;
        this.brevo = brevo;
    }

    /** Envía un correo cualquiera con el proveedor activo. Lanza RestClientException si el proveedor lo rechaza. */
    public void enviar(Correo correo) {
        if (resend.estaConfigurado()) {
            resend.enviar(correo);
        } else {
            brevo.enviar(correo);
        }
    }

    /** Envía el enlace de verificación. Lanza RestClientException si el proveedor rechaza el envío. */
    public void enviarEnlaceVerificacion(String correo, String nombre, String enlace, int horasVigencia) {
        if (resend.estaConfigurado()) {
            resend.enviarEnlaceVerificacion(correo, nombre, enlace, horasVigencia);
        } else {
            brevo.enviarEnlaceVerificacion(correo, nombre, enlace, horasVigencia);
        }
    }

    /**
     * Envía el ticket de un pedido pagado al correo del cliente, con el PDF adjunto.
     * Lanza RestClientException si el proveedor lo rechaza.
     */
    public void enviarTicket(Pedido pedido) {
        Adjunto pdf = new Adjunto(TicketPdf.nombreArchivo(pedido), TicketPdf.generar(pedido));
        enviar(new Correo(pedido.getCorreoCliente(), pedido.getNombreCliente(), PlantillaTicket.asunto(pedido),
                PlantillaTicket.html(pedido), PlantillaTicket.texto(pedido), null, pdf));
    }
}
