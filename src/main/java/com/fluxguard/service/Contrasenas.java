package com.fluxguard.service;

/** Regla de contraseña segura, la misma al crear la cuenta y al cambiarla desde el perfil. */
public final class Contrasenas {

    public static final String REGLA =
            "La contraseña debe tener al menos 8 caracteres, una letra mayúscula y un carácter especial (ej. @, #, $, !, %).";
    private static final String ESPECIALES = "!@#$%^&*(),.?\":{}|<>_-\\/[]~`+=";

    private Contrasenas() {}

    public static boolean esSegura(String contrasena) {
        if (contrasena == null || contrasena.length() < 8) {
            return false;
        }
        boolean mayuscula = false;
        boolean especial = false;
        for (char c : contrasena.toCharArray()) {
            mayuscula |= Character.isUpperCase(c);
            especial |= ESPECIALES.indexOf(c) >= 0;
        }
        return mayuscula && especial;
    }
}
