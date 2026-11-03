package com.fluxguard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Aviso para el dueño de una cuenta (p. ej. "alguien compró en tu cuenta").
 * Se consulta desde la app y la web; si hay dispositivos registrados también se envía como push.
 */
@Document(collection = "notificaciones")
public class Notificacion {

    public static final String COMPRA = "COMPRA";

    /** "compra-FOLIO": así un mismo pedido no puede generar dos avisos. */
    @Id
    private String id;

    @Indexed
    private String usuarioId;

    private String tipo;
    private String titulo;
    private String mensaje;
    private String folio;
    private boolean leida;
    private LocalDateTime fecha;

    public Notificacion() {}

    public Notificacion(String id, String usuarioId, String tipo, String titulo, String mensaje, String folio, LocalDateTime fecha) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.folio = folio;
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

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public boolean isLeida() {
        return leida;
    }

    public void setLeida(boolean leida) {
        this.leida = leida;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
