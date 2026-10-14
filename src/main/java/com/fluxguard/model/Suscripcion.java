package com.fluxguard.model;

import java.time.LocalDate;

/** Suscripción del usuario, guardada dentro del documento de "usuarios". */
public class Suscripcion {

    private String plan;
    private String tipo;
    private String precio;
    private String estado;
    private int circuitos;
    private LocalDate fechaInicio;
    private LocalDate proximoCobro;
    private String transaccionId;

    public Suscripcion() {}

    public Suscripcion(String plan, String tipo, String precio, String estado, int circuitos,
                       LocalDate fechaInicio, LocalDate proximoCobro, String transaccionId) {
        this.plan = plan;
        this.tipo = tipo;
        this.precio = precio;
        this.estado = estado;
        this.circuitos = circuitos;
        this.fechaInicio = fechaInicio;
        this.proximoCobro = proximoCobro;
        this.transaccionId = transaccionId;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getPrecio() {
        return precio;
    }

    public void setPrecio(String precio) {
        this.precio = precio;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public int getCircuitos() {
        return circuitos;
    }

    public void setCircuitos(int circuitos) {
        this.circuitos = circuitos;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getProximoCobro() {
        return proximoCobro;
    }

    public void setProximoCobro(LocalDate proximoCobro) {
        this.proximoCobro = proximoCobro;
    }

    public String getTransaccionId() {
        return transaccionId;
    }

    public void setTransaccionId(String transaccionId) {
        this.transaccionId = transaccionId;
    }
}
