package com.fluxguard;

import com.fluxguard.controller.ProspectoController;
import com.fluxguard.dto.ProspectoRequest;
import com.fluxguard.model.Prospecto;
import com.fluxguard.repository.ProspectoRepository;
import com.fluxguard.service.Correo;
import com.fluxguard.service.CorreoService;
import com.fluxguard.service.LimiteSolicitudes;
import com.fluxguard.service.PlantillaContacto;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContactoTests {

    private ProspectoRepository repo;
    private CorreoService correos;
    private ProspectoController controller;
    private HttpServletRequest http;

    @BeforeEach
    void setUp() {
        repo = mock(ProspectoRepository.class);
        correos = mock(CorreoService.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        http = mock(HttpServletRequest.class);
        when(http.getRemoteAddr()).thenReturn("10.0.0.1");
        controller = new ProspectoController(repo, correos, new LimiteSolicitudes(), "", "fluxguardcorporation@gmail.com");
    }

    private ProspectoRequest solicitud(String correo) {
        ProspectoRequest r = new ProspectoRequest();
        r.setNombre("Irving");
        r.setApellido("Olguin");
        r.setCorreo(correo);
        r.setTelefono("55 1234 5678");
        r.setEmpresa("ITSOEH");
        r.setInteres("Prototipo");
        r.setMensaje("Quiero un kit para el laboratorio <b>urgente</b>");
        return r;
    }

    @Test
    void guardaConFolioYEnviaConfirmacionYAviso() {
        var respuesta = controller.crearProspecto(solicitud("cliente@example.com"), http);

        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        String folio = respuesta.getBody().getProspecto().getFolio();
        assertTrue(folio.matches("SOL-\\d{5}"));
        assertTrue(respuesta.getBody().getMessage().contains(folio));

        ArgumentCaptor<Correo> enviados = ArgumentCaptor.forClass(Correo.class);
        verify(correos, times(2)).enviar(enviados.capture());
        Correo alCliente = enviados.getAllValues().get(0);
        Correo alEquipo = enviados.getAllValues().get(1);

        assertEquals("cliente@example.com", alCliente.para());
        assertEquals("fluxguardcorporation@gmail.com", alCliente.responderA());
        assertTrue(alCliente.asunto().contains(folio));
        assertTrue(alCliente.html().contains("Solicitud de Prototipo Piloto"));

        assertEquals("fluxguardcorporation@gmail.com", alEquipo.para());
        assertEquals("cliente@example.com", alEquipo.responderA());
        assertTrue(alEquipo.html().contains("55 1234 5678"));
        // Lo que escribió el cliente se escapa: no se puede inyectar HTML en el correo
        assertFalse(alEquipo.html().contains("<b>urgente</b>"));
        assertTrue(alEquipo.html().contains("&lt;b&gt;urgente&lt;/b&gt;"));
    }

    @Test
    void siFallaElCorreoLaSolicitudSeGuardaIgual() {
        doThrow(new RestClientException("Resend caído")).when(correos).enviar(any());

        var respuesta = controller.crearProspecto(solicitud("cliente@example.com"), http);

        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        verify(repo).save(any());
        assertFalse(respuesta.getBody().getMessage().contains("Te enviamos una confirmación"));
    }

    @Test
    void campoTrampaLlenoNoGuardaNiEnvia() {
        ProspectoRequest robot = solicitud("victima@example.com");
        robot.setSitioWeb("http://spam.example");

        var respuesta = controller.crearProspecto(robot, http);

        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        verify(repo, never()).save(any());
        verifyNoInteractions(correos);
    }

    @Test
    void mismoCorreoEnPocosMinutosSeRechaza() {
        when(repo.existsByCorreoAndFechaRegistroAfter(eq("cliente@example.com"), any())).thenReturn(true);

        var respuesta = controller.crearProspecto(solicitud("cliente@example.com"), http);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, respuesta.getStatusCode());
        verifyNoInteractions(correos);
    }

    @Test
    void demasiadasSolicitudesDesdeLaMismaIpSeRechazan() {
        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.CREATED, controller.crearProspecto(solicitud("c" + i + "@example.com"), http).getStatusCode());
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, controller.crearProspecto(solicitud("c9@example.com"), http).getStatusCode());

        // Detrás del proxy de Render se usa la IP de X-Forwarded-For
        HttpServletRequest otra = mock(HttpServletRequest.class);
        when(otra.getHeader("X-Forwarded-For")).thenReturn("200.1.2.3, 10.0.0.1");
        assertEquals(HttpStatus.CREATED, controller.crearProspecto(solicitud("c10@example.com"), otra).getStatusCode());
    }

    @Test
    void limiteSolicitudesRespetaLaVentana() {
        LimiteSolicitudes limite = new LimiteSolicitudes();
        assertTrue(limite.permitir("x", 2, Duration.ofHours(1)));
        assertTrue(limite.permitir("x", 2, Duration.ofHours(1)));
        assertFalse(limite.permitir("x", 2, Duration.ofHours(1)));
        assertTrue(limite.permitir("y", 2, Duration.ofHours(1)));
    }

    @Test
    void etiquetasLegiblesDelFormulario() {
        assertEquals("Solicitud de Prototipo Piloto", PlantillaContacto.interes("Prototipo"));
        assertEquals("Sin especificar", PlantillaContacto.sector(""));
        Prospecto p = new Prospecto("Ana", "Lopez", "ana@x.com", "5512345678", "", "", "", "", "");
        p.setFolio("SOL-12345");
        assertNull(PlantillaContacto.confirmacionCliente(p, null, 48).responderA());
    }
}
