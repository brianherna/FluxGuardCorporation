package com.fluxguard.dto;

import java.time.LocalDateTime;

public class UsuarioDto {
    private String id;
    private String nombre;
    private String apellido;
    private String correo;
    private LocalDateTime fechaRegistro;

    public UsuarioDto() {}

    public UsuarioDto(String id, String nombre, String apellido, String correo, LocalDateTime fechaRegistro) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.fechaRegistro = fechaRegistro;
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

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}
