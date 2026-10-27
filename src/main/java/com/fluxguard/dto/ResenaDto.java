package com.fluxguard.dto;

import com.fluxguard.model.Resena;

import java.time.LocalDateTime;

/** Lo que se publica de una reseña: sin id de usuario ni folio. */
public record ResenaDto(String productoId, String productoNombre, String autor, int calificacion,
                        String comentario, LocalDateTime fecha) {

    public static ResenaDto de(Resena resena) {
        return new ResenaDto(resena.getProductoId(), resena.getProductoNombre(), resena.getAutor(),
                resena.getCalificacion(), resena.getComentario(), resena.getFecha());
    }
}
