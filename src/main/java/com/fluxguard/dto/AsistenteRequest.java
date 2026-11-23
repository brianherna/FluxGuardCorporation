package com.fluxguard.dto;

import java.util.List;

public class AsistenteRequest {
    private List<Mensaje> mensajes;

    public List<Mensaje> getMensajes() {
        return mensajes;
    }

    public void setMensajes(List<Mensaje> mensajes) {
        this.mensajes = mensajes;
    }

    /** rol: "usuario" o "asistente". */
    public static class Mensaje {
        private String rol;
        private String texto;

        public Mensaje() {}

        public Mensaje(String rol, String texto) {
            this.rol = rol;
            this.texto = texto;
        }

        public String getRol() {
            return rol;
        }

        public void setRol(String rol) {
            this.rol = rol;
        }

        public String getTexto() {
            return texto;
        }

        public void setTexto(String texto) {
            this.texto = texto;
        }
    }
}
