package com.fluxguard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T usuario;
    private T prospecto;
    private T prospectos;
    private Boolean requiereVerificacion;
    private String correo;

    public ApiResponse() {}

    public ApiResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, message);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getUsuario() {
        return usuario;
    }

    public void setUsuario(T usuario) {
        this.usuario = usuario;
    }

    public T getProspecto() {
        return prospecto;
    }

    public void setProspecto(T prospecto) {
        this.prospecto = prospecto;
    }

    public T getProspectos() {
        return prospectos;
    }

    public void setProspectos(T prospectos) {
        this.prospectos = prospectos;
    }

    public Boolean getRequiereVerificacion() {
        return requiereVerificacion;
    }

    public void setRequiereVerificacion(Boolean requiereVerificacion) {
        this.requiereVerificacion = requiereVerificacion;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }
}
