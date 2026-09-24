package com.fluxguard.controller;

import com.fluxguard.dto.ApiResponse;
import com.fluxguard.dto.ProspectoRequest;
import com.fluxguard.model.Prospecto;
import com.fluxguard.repository.ProspectoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/prospectos")
@CrossOrigin(origins = "*")
public class ProspectoController {

    private final ProspectoRepository prospectoRepository;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-()\\s]{7,30}$");

    public ProspectoController(ProspectoRepository prospectoRepository) {
        this.prospectoRepository = prospectoRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Prospecto>> crearProspecto(@RequestBody ProspectoRequest request) {
        String nombre = request.getNombre() != null ? request.getNombre().trim() : "";
        String apellido = request.getApellido() != null ? request.getApellido().trim() : "";
        String correo = request.getCorreo() != null ? request.getCorreo().trim().toLowerCase() : "";
        String telefono = request.getTelefono() != null ? request.getTelefono().trim() : "";

        String empresa = request.getEmpresa() != null ? request.getEmpresa().trim() : "";
        String cargo = request.getCargo() != null ? request.getCargo().trim() : "";
        String sector = request.getSector() != null ? request.getSector().trim() : "";
        String interes = request.getInteres() != null ? request.getInteres().trim() : "";
        String mensaje = request.getMensaje() != null ? request.getMensaje().trim() : "";

        if (nombre.isEmpty() || apellido.isEmpty() || correo.isEmpty() || telefono.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Nombre, apellido, correo y teléfono son obligatorios."));
        }

        if (nombre.length() > 100 || apellido.length() > 100 || correo.length() > 150 || telefono.length() > 30) {
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

        Prospecto prospecto = new Prospecto(
                nombre, apellido, correo, telefono,
                empresa, cargo, sector, interes, mensaje
        );

        Prospecto guardado = prospectoRepository.save(prospecto);

        ApiResponse<Prospecto> response = ApiResponse.ok("Tus datos fueron enviados correctamente. Nos pondremos en contacto contigo.");
        response.setProspecto(guardado);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Prospecto>>> obtenerProspectos() {
        List<Prospecto> lista = prospectoRepository.findAllByOrderByFechaRegistroDesc();
        ApiResponse<List<Prospecto>> response = ApiResponse.ok("Prospectos obtenidos");
        response.setProspectos(lista);
        return ResponseEntity.ok(response);
    }
}
