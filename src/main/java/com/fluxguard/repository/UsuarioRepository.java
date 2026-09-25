package com.fluxguard.repository;

import com.fluxguard.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {
    Optional<Usuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
    Optional<Usuario> findByTokenVerificacionHash(String tokenVerificacionHash);
}
