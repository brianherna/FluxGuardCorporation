package com.fluxguard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Las páginas se sirven solo desde el dominio de APP_BASE_URL. La sesión vive en el localStorage de cada dominio
 * y PayPal y Stripe regresan siempre a APP_BASE_URL: quien comprara desde otro dominio (p. ej. el de Render)
 * regresaría sin sesión y su pago no se confirmaría. /api/ no se redirige: ahí llegan los webhooks y el health check.
 */
@Component
public class DominioPrincipalFilter extends OncePerRequestFilter {

    private static final Set<String> HOSTS_LOCALES = Set.of("localhost", "127.0.0.1");

    private final String appBaseUrl;
    private final String hostPrincipal;

    public DominioPrincipalFilter(@Value("${app.base-url:${APP_BASE_URL:http://localhost:8080}}") String appBaseUrl) {
        this.appBaseUrl = appBaseUrl.trim().replaceAll("/+$", "");
        this.hostPrincipal = URI.create(this.appBaseUrl).getHost();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        boolean lectura = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        return !lectura
                || hostPrincipal == null
                || HOSTS_LOCALES.contains(hostPrincipal)
                || request.getRequestURI().startsWith("/api/")
                || hostPrincipal.equalsIgnoreCase(request.getServerName());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String consulta = request.getQueryString();
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader(HttpHeaders.LOCATION, appBaseUrl + request.getRequestURI() + (consulta == null ? "" : "?" + consulta));
    }
}
