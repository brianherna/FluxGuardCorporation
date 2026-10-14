package com.fluxguard.service;

import com.fluxguard.model.Producto;
import com.fluxguard.model.Producto.Categoria;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Catálogo de la tienda. Para cambiar un precio o agregar un producto, edita esta lista. */
@Service
public class CatalogoService {

    public static final String KIT_PILOTO = "kit-piloto";

    private static final List<Producto> PRODUCTOS = List.of(
            new Producto(KIT_PILOTO, "Kit Piloto FluxGuard",
                    "Microcontrolador con sensor de corriente no invasivo, conexión WiFi/MQTT y dashboard activado.",
                    Categoria.PROTOTIPO, 350, "PAGO ÚNICO", "bi-cpu", false, 10),
            new Producto("carcasa-personalizada", "Carcasa personalizada",
                    "Carcasa impresa en 3D con el color y el logo de tu empresa.",
                    Categoria.PERSONALIZACION, 180, "POR KIT", "bi-box-seam", true, 10),
            new Producto("pantalla-oled", "Pantalla OLED integrada",
                    "Muestra voltaje, corriente y consumo directamente en el equipo.",
                    Categoria.PERSONALIZACION, 150, "POR KIT", "bi-display", true, 10),
            new Producto("bateria-respaldo", "Batería de respaldo",
                    "Mantiene el monitoreo y el registro de datos aunque se vaya la luz.",
                    Categoria.PERSONALIZACION, 200, "POR KIT", "bi-battery-charging", true, 10),
            new Producto("garantia-12m", "Garantía extendida 12 meses",
                    "Reemplazo del hardware del kit por fallas de fabricación durante un año adicional.",
                    Categoria.SERVICIO, 250, "POR KIT", "bi-shield-check", true, 10),
            new Producto("plan-free", "Plan Free",
                    "Monitoreo básico de 1 circuito con historial de 7 días.",
                    Categoria.PLAN, 0, "GRATIS", "bi-lightning-charge", false, 1),
            new Producto("plan-standard", "Plan Standard",
                    "Hasta 5 circuitos, alertas automáticas, reportes PDF/CSV y 12 meses de historial.",
                    Categoria.PLAN, 499, "MXN / MES", "bi-graph-up-arrow", false, 1)
    );

    public List<Producto> todos() {
        return PRODUCTOS;
    }

    public Optional<Producto> buscar(String id) {
        return PRODUCTOS.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** Tipo de suscripción que activa un plan del catálogo ("plan-standard" → "standard"). */
    public static String tipoDePlan(Producto plan) {
        return plan.id().substring("plan-".length());
    }
}
