package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.ProspectoRequest;
import com.fluxguard.model.Prospecto;
import com.fluxguard.repository.ProspectoRepository;
import com.fluxguard.service.CorreoService;
import com.fluxguard.service.LimiteSolicitudes;
import com.fluxguard.service.PlantillaContacto;
import com.fluxguard.service.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Formulario de contacto (contacto.html). Guarda la solicitud con un folio, manda al cliente un correo de
 * confirmación y avisa al equipo (CONTACTO_CORREO_EQUIPO). Tiene límites para que no se use para mandar spam.
 */
@RestController
@RequestMapping("/api/prospectos")
public class ProspectoController {

    private static final Logger log = LoggerFactory.getLogger(ProspectoController.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-()\\s]{7,30}$");
    private static final int HORAS_RESPUESTA = 48;
    private static final int MAX_POR_IP_POR_HORA = 5;
    private static final Duration ESPERA_MISMO_CORREO = Duration.ofMinutes(10);

    private final ProspectoRepository prospectoRepository;
    private final CorreoService correoService;
    private final LimiteSolicitudes limite;
    private final String adminApiKey;
    private final String correoEquipo;
    private final SecureRandom random = new SecureRandom();

    public ProspectoController(ProspectoRepository prospectoRepository,
                               CorreoService correoService,
                               LimiteSolicitudes limite,
                               @Value("${app.admin-api-key:${ADMIN_API_KEY:}}") String adminApiKey,
                               @Value("${app.contacto.correo-equipo:${CONTACTO_CORREO_EQUIPO:}}") String correoEquipo) {
        this.prospectoRepository = prospectoRepository;
        this.correoService = correoService;
        this.limite = limite;
        this.adminApiKey = adminApiKey.trim();
        this.correoEquipo = correoEquipo.trim();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Prospecto>> crearProspecto(@RequestBody ProspectoRequest request, HttpServletRequest http) {
        String nombre = limpiar(request.getNombre());
        String apellido = limpiar(request.getApellido());
        String correo = limpiar(request.getCorreo()).toLowerCase();
        String telefono = limpiar(request.getTelefono());

        String empresa = limpiar(request.getEmpresa());
        String cargo = limpiar(request.getCargo());
        String sector = limpiar(request.getSector());
        String interes = limpiar(request.getInteres());
        String mensaje = limpiar(request.getMensaje());

        // Campo trampa lleno = robot: se responde como si todo saliera bien, pero no se guarda ni se envía nada
        if (!limpiar(request.getSitioWeb()).isEmpty()) {
            log.warn("[FluxG] Formulario de contacto descartado: campo trampa lleno (IP {}).", ip(http));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("Tus datos fueron enviados correctamente. Nos pondremos en contacto contigo."));
        }

        if (nombre.isEmpty() || apellido.isEmpty() || correo.isEmpty() || telefono.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Nombre, apellido, correo y teléfono son obligatorios."));
        }

        if (nombre.length() > 100 || apellido.length() > 100 || correo.length() > 150 || telefono.length() > 30
                || empresa.length() > 150 || cargo.length() > 100 || sector.length() > 50 || interes.length() > 50
                || mensaje.length() > 2000) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Uno de los campos supera la longitud permitida."));
        }

        if (!EMAIL_PATTERN.matcher(correo).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("El correo electrónico no es válido."));
        }

        if (!PHONE_PATTERN.matcher(telefono).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("El número de teléfono no es válido."));
        }

        if (prospectoRepository.existsByCorreoAndFechaRegistroAfter(correo, LocalDateTime.now().minus(ESPERA_MISMO_CORREO))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Ya recibimos una solicitud con este correo hace unos minutos. Revisa tu bandeja de entrada; te contactaremos pronto."));
        }

        if (!limite.permitir("contacto:" + ip(http), MAX_POR_IP_POR_HORA, Duration.ofHours(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Recibimos demasiadas solicitudes desde tu conexión. Intenta de nuevo más tarde."));
        }

        Prospecto prospecto = new Prospecto(nombre, apellido, correo, telefono, empresa, cargo, sector, interes, mensaje);
        prospecto.setFolio(nuevoFolio());
        Prospecto guardado = prospectoRepository.save(prospecto);

        // La solicitud ya quedó guardada: si un correo falla, se registra pero no se le muestra error al cliente
        boolean confirmacionEnviada = enviar("confirmación al cliente", () ->
                correoService.enviar(PlantillaContacto.confirmacionCliente(guardado, correoEquipo.isEmpty() ? null : correoEquipo, HORAS_RESPUESTA)));
        if (!correoEquipo.isEmpty()) {
            enviar("aviso al equipo", () -> correoService.enviar(PlantillaContacto.avisoEquipo(guardado, correoEquipo)));
        } else {
            log.warn("[FluxG] CONTACTO_CORREO_EQUIPO vacío: nadie recibe aviso de la solicitud {}.", guardado.getFolio());
        }

        ApiResponse<Prospecto> response = ApiResponse.ok(confirmacionEnviada
                ? "¡Listo! Recibimos tu solicitud " + guardado.getFolio() + ". Te enviamos una confirmación a " + correo
                        + " y un especialista te contactará en máximo " + HORAS_RESPUESTA + " horas hábiles."
                : "¡Listo! Recibimos tu solicitud " + guardado.getFolio() + ". Un especialista te contactará en máximo "
                        + HORAS_RESPUESTA + " horas hábiles.");
        response.setProspecto(guardado);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Contiene datos personales: solo responde con el encabezado X-Admin-Key igual a ADMIN_API_KEY. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Prospecto>>> obtenerProspectos(
            @RequestHeader(value = "X-Admin-Key", required = false) String clave) {
        if (adminApiKey.isEmpty() || clave == null || !Tokens.iguales(clave, adminApiKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("No tienes permiso para consultar los prospectos."));
        }

        List<Prospecto> lista = prospectoRepository.findAllByOrderByFechaRegistroDesc();
        ApiResponse<List<Prospecto>> response = ApiResponse.ok("Prospectos obtenidos");
        response.setProspectos(lista);
        return ResponseEntity.ok(response);
    }

    private boolean enviar(String que, Runnable envio) {
        try {
            envio.run();
            return true;
        } catch (RestClientException e) {
            log.error("[FluxG] No se pudo enviar el correo de {}: {}", que, e.getMessage());
            return false;
        }
    }

    private String nuevoFolio() {
        String folio;
        int intentos = 0;
        do {
            folio = "SOL-" + (10000 + random.nextInt(90000));
        } while (prospectoRepository.existsByFolio(folio) && ++intentos < 5);
        return folio;
    }

    /** IP real del cliente. En Render la app está detrás de un proxy que la manda en X-Forwarded-For. */
    private static String ip(HttpServletRequest http) {
        String reenviada = http.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    private static String limpiar(String valor) {
        return valor != null ? valor.trim() : "";
    }
}
