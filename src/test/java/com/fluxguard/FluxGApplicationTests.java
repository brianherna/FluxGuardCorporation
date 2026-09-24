package com.fluxguard;

import com.fluxguard.model.Usuario;
import com.fluxguard.model.Prospecto;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class FluxGApplicationTests {

    @Test
    void testPasswordHashing() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "PasswordSegura123";
        String encoded = encoder.encode(rawPassword);

        assertNotNull(encoded);
        assertTrue(encoder.matches(rawPassword, encoded));
        assertFalse(encoder.matches("wrongPassword", encoded));
    }

    @Test
    void testUsuarioModel() {
        Usuario usuario = new Usuario("Test", "User", "test@fluxguard.io", "hashedPass");
        assertEquals("Test", usuario.getNombre());
        assertEquals("User", usuario.getApellido());
        assertEquals("test@fluxguard.io", usuario.getCorreo());
        assertNotNull(usuario.getFechaRegistro());
    }

    @Test
    void testProspectoModel() {
        Prospecto prospecto = new Prospecto("Carlos", "Gomez", "carlos@empresa.com", "5512345678",
                "CFE", "Ingeniero", "Energia", "Monitoreo", "Mensaje de prueba");
        assertEquals("Carlos", prospecto.getNombre());
        assertEquals("CFE", prospecto.getEmpresa());
        assertEquals("Monitoreo", prospecto.getInteres());
        assertNotNull(prospecto.getFechaRegistro());
    }
}
