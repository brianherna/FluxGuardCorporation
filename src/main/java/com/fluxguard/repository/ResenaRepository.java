package com.fluxguard.repository;

import com.fluxguard.model.Resena;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResenaRepository extends MongoRepository<Resena, String> {
    List<Resena> findByUsuarioId(String usuarioId);
    boolean existsByUsuarioIdAndProductoId(String usuarioId, String productoId);
    List<Resena> findTop30ByCalificacionGreaterThanEqualOrderByFechaDesc(int calificacionMinima);
}
