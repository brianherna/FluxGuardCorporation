package com.fluxguard.repository;

import com.fluxguard.model.Resena;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ResenaRepository extends MongoRepository<Resena, String> {
    List<Resena> findByUsuarioId(String usuarioId);
    boolean existsByUsuarioIdAndProductoId(String usuarioId, String productoId);
    // Reseñas por sección: planes (productoId en la lista de planes) o tienda (todo lo demás)
    List<Resena> findTop30ByCalificacionGreaterThanEqualAndProductoIdInOrderByFechaDesc(int calificacionMinima, Collection<String> productoIds);
    List<Resena> findTop30ByCalificacionGreaterThanEqualAndProductoIdNotInOrderByFechaDesc(int calificacionMinima, Collection<String> productoIds);
    List<Resena> findTop50ByProductoIdInOrderByFechaDesc(Collection<String> productoIds);
    List<Resena> findTop50ByProductoIdNotInOrderByFechaDesc(Collection<String> productoIds);
}
