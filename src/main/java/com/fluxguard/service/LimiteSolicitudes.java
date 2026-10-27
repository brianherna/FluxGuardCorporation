package com.fluxguard.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite simple en memoria de cuántas veces se puede hacer algo por clave (p. ej. por IP) en una ventana de tiempo.
 * Se reinicia si el servidor se reinicia; es suficiente para frenar a quien intente usar el formulario para mandar spam.
 */
@Service
public class LimiteSolicitudes {

    private final Map<String, Deque<Instant>> registros = new ConcurrentHashMap<>();

    /** Registra el intento y devuelve true si todavía está dentro del límite. */
    public boolean permitir(String clave, int maximo, Duration ventana) {
        Instant ahora = Instant.now();
        Instant limite = ahora.minus(ventana);
        Deque<Instant> intentos = registros.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (intentos) {
            while (!intentos.isEmpty() && intentos.peekFirst().isBefore(limite)) {
                intentos.pollFirst();
            }
            if (intentos.size() >= maximo) {
                return false;
            }
            intentos.addLast(ahora);
        }
        if (registros.size() > 10_000) {
            // Evita que la memoria crezca sin fin: se descartan las claves sin intentos recientes
            registros.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(limite);
                }
            });
        }
        return true;
    }
}
