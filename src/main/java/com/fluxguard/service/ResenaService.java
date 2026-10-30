package com.fluxguard.service;

import com.fluxguard.model.Pedido;
import com.fluxguard.model.Producto;
import com.fluxguard.model.Resena;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.ResenaRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reglas de las reseñas:
 * - Solo opina quien tiene cuenta y compró (y pagó) el producto: se valida contra el pedido.
 * - Una reseña por usuario y producto.
 * - Sin sesión solo se publican las positivas (4 o 5 estrellas) que tienen comentario.
 * - Con sesión se ven todas, buenas y malas (las más recientes).
 * - Se muestran por sección: las de los planes en Planes y las de los demás productos en la Tienda.
 */
@Service
public class ResenaService {

    public static final int MAX_COMENTARIO = 500;
    public static final int CALIFICACION_POSITIVA = 4;
    private static final int MAX_DESTACADAS = 12;

    /** Error de negocio con un mensaje que se puede mostrar al usuario. */
    public static class ResenaException extends RuntimeException {
        public ResenaException(String mensaje) {
            super(mensaje);
        }
    }

    /** Dónde se muestran las reseñas: la Tienda (hardware, accesorios, servicios) o la sección Planes. */
    public enum Seccion {
        TIENDA, PLANES;

        /** "planes" → PLANES; cualquier otro valor → TIENDA. */
        public static Seccion de(String valor) {
            return "planes".equalsIgnoreCase(valor) ? PLANES : TIENDA;
        }
    }

    private final ResenaRepository resenaRepository;
    private final PedidoRepository pedidoRepository;
    private final CatalogoService catalogo;

    public ResenaService(ResenaRepository resenaRepository, PedidoRepository pedidoRepository, CatalogoService catalogo) {
        this.resenaRepository = resenaRepository;
        this.pedidoRepository = pedidoRepository;
        this.catalogo = catalogo;
    }

    public Resena crear(Usuario usuario, String folio, String productoId, Integer calificacion, String comentario) {
        if (calificacion == null || calificacion < 1 || calificacion > 5) {
            throw new ResenaException("Elige una calificación de 1 a 5 estrellas.");
        }
        String texto = comentario != null ? comentario.trim() : "";
        if (texto.length() > MAX_COMENTARIO) {
            throw new ResenaException("El comentario puede tener hasta " + MAX_COMENTARIO + " caracteres.");
        }

        Pedido pedido = pedidoRepository.findByFolio(folio != null ? folio.trim() : "")
                .filter(p -> usuario.getId().equals(p.getUsuarioId()))
                .orElseThrow(() -> new ResenaException("No encontramos ese pedido en tu cuenta."));
        if (!pedido.estaPagado()) {
            throw new ResenaException("Podrás opinar cuando el pago de tu pedido esté confirmado.");
        }
        Pedido.Linea linea = pedido.getLineas().stream()
                .filter(l -> l.productoId().equals(productoId))
                .findFirst()
                .orElseThrow(() -> new ResenaException("Ese producto no está en tu pedido."));

        if (resenaRepository.existsByUsuarioIdAndProductoId(usuario.getId(), productoId)) {
            throw new ResenaException("Ya calificaste " + linea.nombre() + ". ¡Gracias por tu opinión!");
        }

        Resena resena = new Resena(usuario.getId(), productoId, linea.nombre(), pedido.getFolio(), autor(usuario),
                calificacion, texto, LocalDateTime.now());
        try {
            return resenaRepository.save(resena);
        } catch (DuplicateKeyException e) {
            // Dos envíos al mismo tiempo: el índice único deja pasar solo uno
            throw new ResenaException("Ya calificaste " + linea.nombre() + ". ¡Gracias por tu opinión!");
        }
    }

    /** Productos que el usuario ya calificó. */
    public List<Resena> delUsuario(Usuario usuario) {
        return resenaRepository.findByUsuarioId(usuario.getId());
    }

    /** Reseñas positivas con comentario de la sección, las más recientes primero (para el carrusel público). */
    public List<Resena> destacadas(Seccion seccion) {
        List<String> planes = idsDePlanes();
        List<Resena> positivas = seccion == Seccion.PLANES
                ? resenaRepository.findTop30ByCalificacionGreaterThanEqualAndProductoIdInOrderByFechaDesc(CALIFICACION_POSITIVA, planes)
                : resenaRepository.findTop30ByCalificacionGreaterThanEqualAndProductoIdNotInOrderByFechaDesc(CALIFICACION_POSITIVA, planes);
        return positivas.stream()
                .filter(r -> r.getComentario() != null && !r.getComentario().isBlank())
                .limit(MAX_DESTACADAS)
                .toList();
    }

    /** Todas las reseñas de la sección, buenas y malas, las más recientes primero (solo para usuarios con sesión). */
    public List<Resena> recientes(Seccion seccion) {
        List<String> planes = idsDePlanes();
        return seccion == Seccion.PLANES
                ? resenaRepository.findTop50ByProductoIdInOrderByFechaDesc(planes)
                : resenaRepository.findTop50ByProductoIdNotInOrderByFechaDesc(planes);
    }

    private List<String> idsDePlanes() {
        return catalogo.todos().stream()
                .filter(p -> p.categoria() == Producto.Categoria.PLAN)
                .map(Producto::id)
                .toList();
    }

    /** Solo el nombre y la inicial del apellido: "Ana López" → "Ana L." */
    static String autor(Usuario usuario) {
        String nombre = usuario.getNombre() != null ? usuario.getNombre().trim().split("\\s+")[0] : "";
        String apellido = usuario.getApellido() != null ? usuario.getApellido().trim() : "";
        String inicial = apellido.isEmpty() ? "" : " " + apellido.substring(0, 1).toUpperCase() + ".";
        return (nombre.isEmpty() ? "Cliente" : nombre) + inicial;
    }
}
