package com.fluxguard.dto;

import java.util.List;

/**
 * Carrito con precios calculados por el servidor.
 *
 * @param avisos cambios que el servidor hizo al carrito (p. ej. accesorios quitados al eliminar el kit)
 */
public record CarritoDto(List<Linea> lineas, int total, int cantidadArticulos, List<String> avisos) {

    /** @param maxCantidad máximo que se puede pedir ahora (los accesorios dependen de cuántos kits hay) */
    public record Linea(String productoId, String nombre, String categoria, String icono, String unidad,
                        int precio, int cantidad, int importe, int maxCantidad) {}
}
