package com.fluxguard;

import com.fluxguard.dto.CarritoDto;
import com.fluxguard.model.Carrito;
import com.fluxguard.model.Pedido;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.CarritoRepository;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.UsuarioRepository;
import com.fluxguard.service.CarritoService;
import com.fluxguard.service.CarritoService.CarritoException;
import com.fluxguard.service.CatalogoService;
import com.fluxguard.service.SuscripcionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CarritoServiceTests {

    private CarritoService service;
    private PedidoRepository pedidoRepository;
    private final List<Pedido> pedidos = new ArrayList<>();
    private Carrito guardado;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        CarritoRepository carritoRepository = mock(CarritoRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);

        // Repositorio de carritos en memoria
        when(carritoRepository.findByUsuarioId("u1")).thenAnswer(inv -> Optional.ofNullable(guardado));
        when(carritoRepository.save(any())).thenAnswer(inv -> guardado = inv.getArgument(0));
        when(pedidoRepository.findByUsuarioIdOrderByFechaDesc("u1")).thenAnswer(inv -> pedidos);
        when(pedidoRepository.save(any())).thenAnswer(inv -> {
            pedidos.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new CarritoService(carritoRepository, pedidoRepository, usuarioRepository,
                new CatalogoService(), new SuscripcionService());
        usuario = new Usuario("Ana", "Lopez", "ana@fluxguard.io", "hash");
        usuario.setId("u1");
    }

    private int cantidad(CarritoDto carrito, String productoId) {
        return carrito.lineas().stream().filter(l -> l.productoId().equals(productoId))
                .mapToInt(CarritoDto.Linea::cantidad).sum();
    }

    @Test
    void personalizacionRequiereKitPiloto() {
        CarritoException error = assertThrows(CarritoException.class,
                () -> service.agregar(usuario, "carcasa-personalizada", 1));
        assertTrue(error.getMessage().contains("Kit Piloto"));

        service.agregar(usuario, "kit-piloto", 2);
        CarritoDto carrito = service.agregar(usuario, "pantalla-oled", 2);
        assertEquals(2, cantidad(carrito, "pantalla-oled"));
        assertEquals(350 * 2 + 150 * 2, carrito.total());

        // Más accesorios que kits no se permite
        assertThrows(CarritoException.class, () -> service.agregar(usuario, "pantalla-oled", 1));
    }

    @Test
    void quitarKitAjustaLosAccesorios() {
        service.agregar(usuario, "kit-piloto", 2);
        service.agregar(usuario, "bateria-respaldo", 2);
        service.agregar(usuario, "garantia-12m", 1);

        CarritoDto unKit = service.cambiarCantidad(usuario, "kit-piloto", 1);
        assertEquals(1, cantidad(unKit, "bateria-respaldo"));
        assertFalse(unKit.avisos().isEmpty());

        CarritoDto sinKit = service.cambiarCantidad(usuario, "kit-piloto", 0);
        assertTrue(sinKit.lineas().isEmpty());
    }

    @Test
    void soloUnPlanALaVez() {
        service.agregar(usuario, "plan-free", 1);
        CarritoDto carrito = service.agregar(usuario, "plan-standard", 3);

        assertEquals(1, carrito.lineas().size());
        assertEquals("plan-standard", carrito.lineas().get(0).productoId());
        assertEquals(1, carrito.lineas().get(0).cantidad());
        assertEquals(499, carrito.total());
    }

    @Test
    void comprarCreaPedidoActivaPlanYVaciaCarrito() {
        service.agregar(usuario, "kit-piloto", 1);
        service.agregar(usuario, "carcasa-personalizada", 1);
        service.agregar(usuario, "plan-standard", 1);

        CarritoService.Compra compra = service.comprar(usuario);

        assertEquals(350 + 180 + 499, compra.pedido().getTotal());
        assertEquals(3, compra.pedido().getLineas().size());
        assertEquals("standard", compra.usuario().getSuscripcion().getTipo());
        assertTrue(service.ver(usuario).lineas().isEmpty());
        assertThrows(CarritoException.class, () -> service.comprar(usuario));
    }

    @Test
    void kitCompradoAntesPermiteAccesoriosSinKitEnCarrito() {
        pedidos.add(new Pedido("FLX-P1", "u1",
                List.of(new Pedido.Linea("kit-piloto", "Kit Piloto FluxGuard", 350, 1, 350)),
                350, "Pagado (simulado)", LocalDateTime.now()));

        CarritoDto carrito = service.agregar(usuario, "garantia-12m", 1);
        assertEquals(250, carrito.total());
        assertThrows(CarritoException.class, () -> service.agregar(usuario, "garantia-12m", 1));
    }

    @Test
    void productoInexistenteOCantidadInvalida() {
        assertThrows(CarritoException.class, () -> service.agregar(usuario, "no-existe", 1));
        assertThrows(CarritoException.class, () -> service.agregar(usuario, "kit-piloto", 0));
        assertThrows(CarritoException.class, () -> service.agregar(usuario, "kit-piloto", 11));
    }
}
