package com.fluxguard.repository;

import com.fluxguard.model.Sesion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SesionRepository extends MongoRepository<Sesion, String> {
    Optional<Sesion> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
}
