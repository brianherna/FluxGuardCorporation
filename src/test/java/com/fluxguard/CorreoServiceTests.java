package com.fluxguard;

import com.fluxguard.service.BrevoEmailService;
import com.fluxguard.service.CorreoService;
import com.fluxguard.service.PlantillaVerificacion;
import com.fluxguard.service.ResendEmailService;
import org.junit.jupiter.api.Test;

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
}
