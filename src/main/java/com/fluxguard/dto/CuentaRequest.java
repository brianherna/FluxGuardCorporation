package com.fluxguard.dto;

/**
 * Cambios desde el perfil de la app. Cada ruta usa sus campos:
 * datos (nombre, apellido), contraseña (contrasenaActual, contrasenaNueva) y correo (correo, contrasenaActual).
 */
public class CuentaRequest {
    private String nombre;
    private String apellido;
    private String correo;
    private String contrasenaActual;
    private String contrasenaNueva;

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

    public String getContrasenaActual() {
        return contrasenaActual;
    }

    public void setContrasenaActual(String contrasenaActual) {
        this.contrasenaActual = contrasenaActual;
    }

    public String getContrasenaNueva() {
        return contrasenaNueva;
    }

    public void setContrasenaNueva(String contrasenaNueva) {
        this.contrasenaNueva = contrasenaNueva;
    }
}
