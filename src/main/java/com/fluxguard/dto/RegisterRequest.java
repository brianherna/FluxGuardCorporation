package com.fluxguard.dto;

public class RegisterRequest {
    private String nombre;
    private String apellido;
    private String email;
    private String password;
    /** Casilla "He leído y acepto el Aviso de Privacidad": sin ella no se crea la cuenta. */
    private Boolean aceptaPrivacidad;

    public RegisterRequest() {}

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getAceptaPrivacidad() {
        return aceptaPrivacidad;
    }

    public void setAceptaPrivacidad(Boolean aceptaPrivacidad) {
        this.aceptaPrivacidad = aceptaPrivacidad;
    }
}
