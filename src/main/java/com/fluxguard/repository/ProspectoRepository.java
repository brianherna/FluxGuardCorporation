package com.fluxguard.repository;

import com.fluxguard.model.Prospecto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProspectoRepository extends MongoRepository<Prospecto, String> {
    List<Prospecto> findAllByOrderByFechaRegistroDesc();
    boolean existsByCorreoAndFechaRegistroAfter(String correo, LocalDateTime desde);
    boolean existsByFolio(String folio);
}
