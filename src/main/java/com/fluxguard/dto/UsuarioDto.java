package com.fluxguard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluxguard.model.Suscripcion;
import com.fluxguard.model.Usuario;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsuarioDto {
    private String id;
    private String nombre;
    private String apellido;
    private String correo;
    private LocalDateTime fechaRegistro;
    private Suscripcion suscripcion;

    public UsuarioDto() {}

    public UsuarioDto(String id, String nombre, String apellido, String correo, LocalDateTime fechaRegistro) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.fechaRegistro = fechaRegistro;
    }

    public static UsuarioDto de(Usuario usuario) {
        UsuarioDto dto = new UsuarioDto(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.getFechaRegistro()
        );
        dto.setSuscripcion(usuario.getSuscripcion());
        return dto;
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

    public Suscripcion getSuscripcion() {
        return suscripcion;
    }

    public void setSuscripcion(Suscripcion suscripcion) {
        this.suscripcion = suscripcion;
    }
}
