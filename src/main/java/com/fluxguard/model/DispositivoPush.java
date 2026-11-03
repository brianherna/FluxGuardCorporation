package com.fluxguard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Celular con la app instalada: el token de Firebase al que se envían los push de una cuenta. */
@Document(collection = "dispositivos_push")
public class DispositivoPush {

    @Id
    private String id;

    @Indexed
    private String usuarioId;

    @Indexed(unique = true)
    private String token;

    private String plataforma;
    private LocalDateTime actualizado;

    public DispositivoPush() {}

    public DispositivoPush(String usuarioId, String token, String plataforma, LocalDateTime actualizado) {
        this.usuarioId = usuarioId;
        this.token = token;
        this.plataforma = plataforma;
        this.actualizado = actualizado;
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPlataforma() {
        return plataforma;
    }

    public void setPlataforma(String plataforma) {
        this.plataforma = plataforma;
    }

    public LocalDateTime getActualizado() {
        return actualizado;
    }

    public void setActualizado(LocalDateTime actualizado) {
        this.actualizado = actualizado;
    }
}
