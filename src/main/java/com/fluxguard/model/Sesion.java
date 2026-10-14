package com.fluxguard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Sesión iniciada con /api/auth/login. MongoDB borra el documento automáticamente al llegar a "expira". */
@Document(collection = "sesiones")
public class Sesion {

    @Id
    private String id;

    @Indexed(unique = true)
    private String tokenHash;

    private String usuarioId;
    private LocalDateTime creada;

    @Indexed(expireAfter = "0s")
    private LocalDateTime expira;

    public Sesion() {}

    public Sesion(String tokenHash, String usuarioId, LocalDateTime creada, LocalDateTime expira) {
        this.tokenHash = tokenHash;
        this.usuarioId = usuarioId;
        this.creada = creada;
        this.expira = expira;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public LocalDateTime getCreada() {
        return creada;
    }

    public void setCreada(LocalDateTime creada) {
        this.creada = creada;
    }

    public LocalDateTime getExpira() {
        return expira;
    }

    public void setExpira(LocalDateTime expira) {
        this.expira = expira;
    }
}
