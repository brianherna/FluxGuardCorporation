package com.fluxguard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;

    private String nombre;
    private String apellido;

    @Indexed(unique = true)
    private String correo;

    private String passwordHash;
    private LocalDateTime fechaRegistro;

    // Verificación de correo (Brevo). null = cuenta creada antes de la verificación, se considera verificada.
    private Boolean verificado;
    private String tokenVerificacionHash;
    private LocalDateTime tokenExpira;
    private LocalDateTime tokenEnviadoEn;

    public Usuario() {
        this.fechaRegistro = LocalDateTime.now();
    }

    public Usuario(String nombre, String apellido, String correo, String passwordHash) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.fechaRegistro = LocalDateTime.now();
        this.verificado = false;
    }

    public boolean estaVerificado() {
        return verificado == null || verificado;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public Boolean getVerificado() {
        return verificado;
    }

    public void setVerificado(Boolean verificado) {
        this.verificado = verificado;
    }

    public String getTokenVerificacionHash() {
        return tokenVerificacionHash;
    }

    public void setTokenVerificacionHash(String tokenVerificacionHash) {
        this.tokenVerificacionHash = tokenVerificacionHash;
    }

    public LocalDateTime getTokenExpira() {
        return tokenExpira;
    }

    public void setTokenExpira(LocalDateTime tokenExpira) {
        this.tokenExpira = tokenExpira;
    }

    public LocalDateTime getTokenEnviadoEn() {
        return tokenEnviadoEn;
    }

    public void setTokenEnviadoEn(LocalDateTime tokenEnviadoEn) {
        this.tokenEnviadoEn = tokenEnviadoEn;
    }
}
