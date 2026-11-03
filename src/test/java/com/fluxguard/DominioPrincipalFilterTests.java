package com.fluxguard;

import com.fluxguard.config.DominioPrincipalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class DominioPrincipalFilterTests {

    private static final String PRINCIPAL = "https://fluxguardcorporation.com";
    private static final String RENDER = "fluxguardcorporation.onrender.com";

    private record Resultado(MockHttpServletResponse respuesta, boolean siguio) {}

    private Resultado pedir(String baseUrl, String metodo, String host, String ruta, String consulta) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setServerName(host);
        peticion.setQueryString(consulta);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        new DominioPrincipalFilter(baseUrl).doFilter(peticion, respuesta, cadena);
        return new Resultado(respuesta, cadena.getRequest() != null);
    }

    @Test
    void lasPaginasDeOtroDominioPasanAlPrincipalConservandoLaRuta() throws Exception {
        Resultado r = pedir(PRINCIPAL, "GET", RENDER, "/carrito.html", "pedido=FLX-1&token=ORDEN123");

        assertEquals(301, r.respuesta().getStatus());
        assertEquals(PRINCIPAL + "/carrito.html?pedido=FLX-1&token=ORDEN123", r.respuesta().getHeader("Location"));
        assertFalse(r.siguio());
    }

    @Test
    void laPaginaPrincipalSinConsultaTambienSeRedirige() throws Exception {
        Resultado r = pedir(PRINCIPAL + "/", "GET", RENDER, "/", null);

        assertEquals(PRINCIPAL + "/", r.respuesta().getHeader("Location"));
    }

    @Test
    void enElDominioPrincipalNoSeRedirige() throws Exception {
        assertTrue(pedir(PRINCIPAL, "GET", "fluxguardcorporation.com", "/carrito.html", null).siguio());
        assertTrue(pedir(PRINCIPAL, "GET", "FluxGuardCorporation.com", "/", null).siguio());
    }

    @Test
    void laApiNoSeRedirigeParaNoRomperWebhooksNiElHealthCheck() throws Exception {
        assertTrue(pedir(PRINCIPAL, "GET", RENDER, "/api/health", null).siguio());
        assertTrue(pedir(PRINCIPAL, "POST", RENDER, "/api/pagos/stripe/webhook", null).siguio());
    }

    @Test
    void soloSeRedirigenLecturas() throws Exception {
        assertTrue(pedir(PRINCIPAL, "POST", RENDER, "/carrito.html", null).siguio());
    }

    @Test
    void enDesarrolloLocalNoSeRedirige() throws Exception {
        assertTrue(pedir("http://localhost:8080", "GET", "192.168.1.20", "/index.html", null).siguio());
    }
}
