package com.fluxguard;

import com.fluxguard.model.Pedido;
import com.fluxguard.model.Resena;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.ResenaRepository;
import com.fluxguard.service.ResenaService;
import com.fluxguard.service.ResenaService.ResenaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResenaServiceTests {

    private ResenaService service;
    private PedidoRepository pedidoRepository;
    private final List<Resena> resenas = new ArrayList<>();
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        ResenaRepository resenaRepository = mock(ResenaRepository.class);
        pedidoRepository = mock(PedidoRepository.class);

        // Repositorio de reseñas en memoria
        when(resenaRepository.save(any())).thenAnswer(inv -> {
            resenas.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(resenaRepository.existsByUsuarioIdAndProductoId(anyString(), anyString())).thenAnswer(inv -> resenas.stream()
                .anyMatch(r -> r.getUsuarioId().equals(inv.getArgument(0)) && r.getProductoId().equals(inv.getArgument(1))));
        when(resenaRepository.findTop30ByCalificacionGreaterThanEqualOrderByFechaDesc(anyInt())).thenAnswer(inv -> resenas.stream()
                .filter(r -> r.getCalificacion() >= inv.<Integer>getArgument(0)).toList());

        service = new ResenaService(resenaRepository, pedidoRepository);
        usuario = new Usuario("Ana María", "lopez", "ana@fluxguard.io", "hash");
        usuario.setId("u1");
    }

    private void pedido(String folio, String usuarioId, String estado) {
        Pedido pedido = new Pedido(folio, usuarioId,
                List.of(new Pedido.Linea("kit-piloto", "Kit Piloto FluxGuard", 350, 1, 350)),
                350, estado, LocalDateTime.now());
        when(pedidoRepository.findByFolio(folio)).thenReturn(Optional.of(pedido));
    }

    @Test
    void clienteOpinaSobreUnProductoQuePago() {
        pedido("FLX-P1", "u1", Pedido.PAGADO);

        Resena resena = service.crear(usuario, "FLX-P1", "kit-piloto", 5, "  Muy fácil de instalar.  ");

        assertEquals("Kit Piloto FluxGuard", resena.getProductoNombre());
        assertEquals("Muy fácil de instalar.", resena.getComentario());
        // Solo se publica el primer nombre y la inicial del apellido
        assertEquals("Ana L.", resena.getAutor());
    }

    @Test
    void soloSeOpinaSobrePedidosPropiosPagadosYConEseProducto() {
        pedido("FLX-P2", "otro-usuario", Pedido.PAGADO);
        pedido("FLX-P3", "u1", Pedido.PENDIENTE);
        pedido("FLX-P4", "u1", Pedido.PAGADO);

        assertThrows(ResenaException.class, () -> service.crear(usuario, "FLX-P2", "kit-piloto", 5, ""));
        assertThrows(ResenaException.class, () -> service.crear(usuario, "FLX-P3", "kit-piloto", 5, ""));
        assertThrows(ResenaException.class, () -> service.crear(usuario, "FLX-P4", "pantalla-oled", 5, ""));
        assertThrows(ResenaException.class, () -> service.crear(usuario, "NO-EXISTE", "kit-piloto", 5, ""));
        assertTrue(resenas.isEmpty());
    }

    @Test
    void calificacionYComentarioValidosYUnaReseñaPorProducto() {
        pedido("FLX-P5", "u1", Pedido.PAGADO);

        assertThrows(ResenaException.class, () -> service.crear(usuario, "FLX-P5", "kit-piloto", 0, ""));
        assertThrows(ResenaException.class, () -> service.crear(usuario, "FLX-P5", "kit-piloto", 6, ""));
        assertThrows(ResenaException.class, () -> service.crear(usuario, "FLX-P5", "kit-piloto", null, ""));
        assertThrows(ResenaException.class, () -> service.crear(usuario, "FLX-P5", "kit-piloto", 4, "x".repeat(501)));

        service.crear(usuario, "FLX-P5", "kit-piloto", 4, "");
        assertThrows(ResenaException.class, () -> service.crear(usuario, "FLX-P5", "kit-piloto", 5, "Otra vez"));
        assertEquals(1, resenas.size());
    }

    @Test
    void destacadasSoloPositivasConComentario() {
        resenas.add(new Resena("u1", "kit-piloto", "Kit", "F1", "Ana L.", 5, "Excelente", LocalDateTime.now()));
        resenas.add(new Resena("u2", "kit-piloto", "Kit", "F2", "Luis P.", 4, "", LocalDateTime.now()));
        resenas.add(new Resena("u3", "kit-piloto", "Kit", "F3", "Eva R.", 2, "No me gustó", LocalDateTime.now()));

        List<Resena> destacadas = service.destacadas();

        assertEquals(1, destacadas.size());
        assertEquals("Ana L.", destacadas.get(0).getAutor());
    }
}
