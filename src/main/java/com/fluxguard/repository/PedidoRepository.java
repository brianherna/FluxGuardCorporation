package com.fluxguard.repository;

import com.fluxguard.model.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByUsuarioIdOrderByFechaDesc(String usuarioId);
}
