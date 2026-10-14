package com.fluxguard.service;

import com.fluxguard.model.Suscripcion;
import com.fluxguard.model.Usuario;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/** Planes de suscripción. Se activan cuando se confirma el pago de un pedido que incluye un plan o un kit. */
@Service
public class SuscripcionService {

    private record Plan(String nombre, String precio, int circuitos) {}

    private static final Map<String, Plan> PLANES = Map.of(
            "free", new Plan("Plan Free", "0", 1),
            "standard", new Plan("Plan Standard", "499", 5),
            "ultimate", new Plan("Plan Ultimate", "1,499", 12),
            "prototipo", new Plan("Prototipo Piloto", "350", 5)
    );

    /**
     * Asigna el plan al usuario (sin guardarlo). Lanza IllegalArgumentException si el plan no existe.
     *
     * @param transaccionId folio del pedido con el que se pagó
     */
    public void activar(Usuario usuario, String tipo, String transaccionId) {
        Plan plan = PLANES.get(tipo);
        if (plan == null) {
            throw new IllegalArgumentException("El plan seleccionado no existe.");
        }
        LocalDate hoy = LocalDate.now();
        usuario.setSuscripcion(new Suscripcion(
                plan.nombre(), tipo, plan.precio(), "Activa", plan.circuitos(),
                hoy, hoy.plusMonths(1), transaccionId
        ));
    }
}
