package com.fluxguard.service;

import com.fluxguard.model.Suscripcion;
import com.fluxguard.model.Usuario;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Map;

/** Planes de suscripción. Lo usan pago.html (/api/suscripcion) y la compra desde el carrito. */
@Service
public class SuscripcionService {

    private record Plan(String nombre, String precio, int circuitos) {}

    private static final Map<String, Plan> PLANES = Map.of(
            "free", new Plan("Plan Free", "0", 1),
            "standard", new Plan("Plan Standard", "499", 5),
            "ultimate", new Plan("Plan Ultimate", "1,499", 12),
            "prototipo", new Plan("Prototipo Piloto", "350", 5)
    );

    private final SecureRandom random = new SecureRandom();

    public boolean existe(String tipo) {
        return PLANES.containsKey(tipo);
    }

    /** Asigna el plan al usuario (sin guardarlo). Lanza IllegalArgumentException si el plan no existe. */
    public void activar(Usuario usuario, String tipo) {
        Plan plan = PLANES.get(tipo);
        if (plan == null) {
            throw new IllegalArgumentException("El plan seleccionado no existe.");
        }
        LocalDate hoy = LocalDate.now();
        usuario.setSuscripcion(new Suscripcion(
                plan.nombre(), tipo, plan.precio(), "Activa", plan.circuitos(),
                hoy, hoy.plusMonths(1), "FLX-" + (100000 + random.nextInt(900000))
        ));
    }
}
