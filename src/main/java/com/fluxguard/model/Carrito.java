package com.fluxguard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Carrito de compras de un usuario. Solo guarda id de producto y cantidad; los precios salen del catálogo. */
@Document(collection = "carritos")
public class Carrito {

    @Id
    private String id;

    @Indexed(unique = true)
    private String usuarioId;

    private List<Item> items = new ArrayList<>();
    private LocalDateTime actualizado;

    public Carrito() {}

    public Carrito(String usuarioId) {
        this.usuarioId = usuarioId;
        this.actualizado = LocalDateTime.now();
    }

    public static class Item {
        private String productoId;
        private int cantidad;

        public Item() {}

        public Item(String productoId, int cantidad) {
            this.productoId = productoId;
            this.cantidad = cantidad;
        }

        public String getProductoId() {
            return productoId;
        }

        public void setProductoId(String productoId) {
            this.productoId = productoId;
        }

        public int getCantidad() {
            return cantidad;
        }

        public void setCantidad(int cantidad) {
            this.cantidad = cantidad;
        }
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

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public LocalDateTime getActualizado() {
        return actualizado;
    }

    public void setActualizado(LocalDateTime actualizado) {
        this.actualizado = actualizado;
    }
}
