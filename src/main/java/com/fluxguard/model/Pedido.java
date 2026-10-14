package com.fluxguard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/** Compra confirmada desde el carrito. Guarda nombre y precio de cada línea tal como estaban al comprar. */
@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;

    @Indexed(unique = true)
    private String folio;

    @Indexed
    private String usuarioId;

    private List<Linea> lineas;
    private int total;
    private String estado;
    private LocalDateTime fecha;

    public Pedido() {}

    public Pedido(String folio, String usuarioId, List<Linea> lineas, int total, String estado, LocalDateTime fecha) {
        this.folio = folio;
        this.usuarioId = usuarioId;
        this.lineas = lineas;
        this.total = total;
        this.estado = estado;
        this.fecha = fecha;
    }

    public record Linea(String productoId, String nombre, int precioUnitario, int cantidad, int importe) {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public List<Linea> getLineas() {
        return lineas;
    }

    public void setLineas(List<Linea> lineas) {
        this.lineas = lineas;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
