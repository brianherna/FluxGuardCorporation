package com.fluxguard.repository;

import com.fluxguard.model.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByUsuarioIdOrderByFechaDesc(String usuarioId);
    Optional<Pedido> findByFolio(String folio);
}
