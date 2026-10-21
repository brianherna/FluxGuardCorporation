package com.fluxguard.model;

/**
 * Producto del catálogo de la tienda. No se guarda en MongoDB: el catálogo vive en CatalogoService.
 * Los precios están en pesos mexicanos con IVA incluido.
 *
 * @param requiereKit  true si solo se puede comprar junto con (o después de) un kit piloto
 * @param maxCantidad  máximo por pedido (1 para planes)
 * @param stock        unidades disponibles; null = no aplica (servicios y planes no se agotan)
 * @param garantia     garantía que se muestra en la tienda (ej. "Garantía de 1 año incluida"); null = sin garantía
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
        int maxCantidad,
        Integer stock,
        String garantia
) {
    public enum Categoria { PLAN, PROTOTIPO, PERSONALIZACION, SERVICIO }

    public boolean agotado() {
        return stock != null && stock <= 0;
    }

    /** Lo más que se puede pedir por stock y por límite de pedido (sin contar la regla de un accesorio por kit). */
    public int maximoPedible() {
        return stock == null ? maxCantidad : Math.max(0, Math.min(maxCantidad, stock));
    }
}
