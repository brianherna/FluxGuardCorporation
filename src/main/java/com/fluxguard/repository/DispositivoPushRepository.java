package com.fluxguard.repository;

import com.fluxguard.model.DispositivoPush;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispositivoPushRepository extends MongoRepository<DispositivoPush, String> {
    List<DispositivoPush> findByUsuarioId(String usuarioId);
    Optional<DispositivoPush> findByToken(String token);
    void deleteByToken(String token);
    void deleteByUsuarioIdAndToken(String usuarioId, String token);
}
