package com.fluxguard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Opinión de un cliente sobre un producto que compró (y pagó). Una por usuario y producto.
 * El nombre del autor se guarda ya recortado ("Ana L.") porque es lo único que se publica.
 */
@Document(collection = "resenas")
@CompoundIndex(name = "usuario_producto", def = "{'usuarioId': 1, 'productoId': 1}", unique = true)
public class Resena {

    @Id
    private String id;

    private String usuarioId;
    private String productoId;
    private String productoNombre;
    /** Folio del pedido pagado que permitió opinar. */
    private String folio;
    private String autor;
    private int calificacion;
    private String comentario;

    @Indexed
    private LocalDateTime fecha;

    public Resena() {}

    public Resena(String usuarioId, String productoId, String productoNombre, String folio, String autor,
                  int calificacion, String comentario, LocalDateTime fecha) {
        this.usuarioId = usuarioId;
        this.productoId = productoId;
        this.productoNombre = productoNombre;
        this.folio = folio;
        this.autor = autor;
        this.calificacion = calificacion;
        this.comentario = comentario;
        this.fecha = fecha;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getProductoId() {
        return productoId;
    }

    public void setProductoId(String productoId) {
        this.productoId = productoId;
    }

    public String getProductoNombre() {
        return productoNombre;
    }

    public void setProductoNombre(String productoNombre) {
        this.productoNombre = productoNombre;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public int getCalificacion() {
        return calificacion;
    }

    public void setCalificacion(int calificacion) {
        this.calificacion = calificacion;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
