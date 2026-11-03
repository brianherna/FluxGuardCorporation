package com.fluxguard.repository;

import com.fluxguard.model.Notificacion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends MongoRepository<Notificacion, String> {
    List<Notificacion> findTop50ByUsuarioIdOrderByFechaDesc(String usuarioId);
    List<Notificacion> findByUsuarioIdAndLeidaFalse(String usuarioId);
    long countByUsuarioIdAndLeidaFalse(String usuarioId);
}
