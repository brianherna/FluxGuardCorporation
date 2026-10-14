package com.fluxguard.model;

/**
 * Producto del catálogo de la tienda. No se guarda en MongoDB: el catálogo vive en CatalogoService.
 * Los precios están en pesos mexicanos con IVA incluido.
 *
 * @param requiereKit  true si solo se puede comprar junto con (o después de) un kit piloto
 * @param maxCantidad  máximo por pedido (1 para planes)
 */
public record Producto(
        String id,
        String nombre,
        String descripcion,
        Categoria categoria,
        int precio,
        String unidad,
        String icono,
        boolean requiereKit,
        int maxCantidad
) {
    public enum Categoria { PLAN, PROTOTIPO, PERSONALIZACION, SERVICIO }
}
