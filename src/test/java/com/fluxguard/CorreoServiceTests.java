package com.fluxguard;

import com.fluxguard.model.Pedido;
import com.fluxguard.service.BrevoEmailService;
import com.fluxguard.service.Correo;
import com.fluxguard.service.CorreoService;
import com.fluxguard.service.PlantillaTicket;
import com.fluxguard.service.PlantillaVerificacion;
import com.fluxguard.service.ResendEmailService;
import com.fluxguard.service.TicketPdf;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorreoServiceTests {

    @Test
    void usaResendCuandoTieneApiKey() {
        ResendEmailService resend = mock(ResendEmailService.class);
        BrevoEmailService brevo = mock(BrevoEmailService.class);
        when(resend.estaConfigurado()).thenReturn(true);

        new CorreoService(resend, brevo).enviarEnlaceVerificacion("ana@test.com", "Ana", "http://x/verify", 24);

        verify(resend).enviarEnlaceVerificacion("ana@test.com", "Ana", "http://x/verify", 24);
        verifyNoInteractions(brevo);
    }

    @Test
    void sinResendUsaBrevo() {
        ResendEmailService resend = mock(ResendEmailService.class);
        BrevoEmailService brevo = mock(BrevoEmailService.class);
        when(resend.estaConfigurado()).thenReturn(false);

        new CorreoService(resend, brevo).enviarEnlaceVerificacion("ana@test.com", "Ana", "http://x/verify", 24);

        verify(brevo).enviarEnlaceVerificacion("ana@test.com", "Ana", "http://x/verify", 24);
        verify(resend, never()).enviarEnlaceVerificacion(any(), any(), any(), anyInt());
    }

    @Test
    void laPlantillaEscapaElNombreYLlevaElEnlace() {
        String html = PlantillaVerificacion.html("<script>", "https://fluxguardcorporation.com/api/auth/verify?token=abc", 24);

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("https://fluxguardcorporation.com/api/auth/verify?token=abc"));
        assertTrue(PlantillaVerificacion.texto("Ana", "https://x", 24).contains("24 horas"));
    }

    private Pedido pedidoPagado() {
        Pedido pedido = new Pedido("FLX-P48213377", "u1", List.of(
                new Pedido.Linea("kit-piloto", "Kit Piloto <FluxGuard>", 350, 2, 700),
                new Pedido.Linea("pantalla-oled", "Pantalla OLED integrada", 150, 1, 150)),
                850, Pedido.PAGADO, LocalDateTime.of(2026, 10, 27, 18, 5));
        pedido.setPagadoEn(LocalDateTime.of(2026, 10, 27, 18, 6));
        pedido.setMetodoPago("stripe_test");
        pedido.setNombreCliente("Ana López");
        pedido.setCorreoCliente("ana@test.com");
        return pedido;
    }

    @Test
    void elTicketSeEnviaConElMismoProveedorQueLaVerificacion() {
        ResendEmailService resend = mock(ResendEmailService.class);
        BrevoEmailService brevo = mock(BrevoEmailService.class);
        Pedido pedido = pedidoPagado();

        // Va al cliente, con el folio en el asunto y el PDF del ticket adjunto
        ArgumentMatcher<Correo> ticket = c -> c.para().equals("ana@test.com")
                && c.nombrePara().equals("Ana López")
                && c.asunto().contains("FLX-P48213377")
                && c.adjunto() != null
                && c.adjunto().nombre().equals("ticket-FLX-P48213377.pdf")
                && new String(c.adjunto().contenido(), 0, 5).equals("%PDF-");

        when(resend.estaConfigurado()).thenReturn(false);
        new CorreoService(resend, brevo).enviarTicket(pedido);
        verify(brevo).enviar(argThat(ticket));

        when(resend.estaConfigurado()).thenReturn(true);
        new CorreoService(resend, brevo).enviarTicket(pedido);
        verify(resend).enviar(argThat(ticket));
    }

    @Test
    void elPdfDelTicketTraeFolioProductosEIva() throws Exception {
        Pedido pedido = pedidoPagado();
        // Caracteres que no existen en las fuentes del PDF no deben romperlo
        pedido.setNombreCliente("Ana López 李");

        try (PDDocument pdf = Loader.loadPDF(TicketPdf.generar(pedido))) {
            String texto = new PDFTextStripper().getText(pdf);
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(texto.contains("FLX-P48213377"));
            assertTrue(texto.contains("Kit Piloto <FluxGuard>"));
            assertTrue(texto.contains("$117.24"));
            assertTrue(texto.contains("$850.00"));
            assertTrue(texto.contains("garantías"));
            assertTrue(texto.contains("Ana López ?"));
        }
    }

    @Test
    void elTicketDesglosaElIvaYEscapaLosDatos() {
        Pedido pedido = pedidoPagado();
        String html = PlantillaTicket.html(pedido);
        String texto = PlantillaTicket.texto(pedido);

        // $850 con IVA incluido = $732.76 + $117.24 de IVA
        assertEquals(new BigDecimal("117.24"), PlantillaTicket.iva(850));
        assertTrue(texto.contains("117.24"));
        assertTrue(texto.contains("732.76"));
        assertTrue(texto.contains("27/10/2026 18:06"));
        assertTrue(texto.contains("Tarjeta (modo de prueba)"));
        assertTrue(html.contains("FLX-P48213377"));
        assertFalse(html.contains("<FluxGuard>"));
        assertTrue(html.contains("Kit Piloto &lt;FluxGuard&gt;"));
    }
}
