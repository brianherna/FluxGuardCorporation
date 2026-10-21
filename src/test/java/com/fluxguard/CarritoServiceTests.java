package com.fluxguard;

import com.fluxguard.dto.CarritoDto;
import com.fluxguard.model.Carrito;
import com.fluxguard.model.Pedido;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.CarritoRepository;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.service.CarritoService;
import com.fluxguard.service.CarritoService.CarritoException;
import com.fluxguard.service.CatalogoService;
import com.fluxguard.service.Comprador;
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
    private final java.util.Map<String, Carrito> carritos = new java.util.HashMap<>();
    private Usuario usuario;
    private Comprador comprador;

    @BeforeEach
    void setUp() {
        CarritoRepository carritoRepository = mock(CarritoRepository.class);
        pedidoRepository = mock(PedidoRepository.class);

        // Repositorio de carritos en memoria
        when(carritoRepository.findByUsuarioId(anyString())).thenAnswer(inv -> Optional.ofNullable(carritos.get(inv.<String>getArgument(0))));
        when(carritoRepository.save(any())).thenAnswer(inv -> {
            Carrito c = inv.getArgument(0);
            carritos.put(c.getUsuarioId(), c);
            return c;
        });
        when(pedidoRepository.findByUsuarioIdOrderByFechaDesc("u1")).thenAnswer(inv -> pedidos);
        when(pedidoRepository.save(any())).thenAnswer(inv -> {
            pedidos.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        service = new CarritoService(carritoRepository, pedidoRepository, new CatalogoService());
        usuario = new Usuario("Ana", "Lopez", "ana@fluxguard.io", "hash");
        usuario.setId("u1");
        comprador = Comprador.de(usuario);
    }

    private int cantidad(CarritoDto carrito, String productoId) {
        return carrito.lineas().stream().filter(l -> l.productoId().equals(productoId))
                .mapToInt(CarritoDto.Linea::cantidad).sum();
    }

    @Test
    void personalizacionRequiereKitPiloto() {
        CarritoException error = assertThrows(CarritoException.class,
                () -> service.agregar(comprador, "carcasa-personalizada", 1));
        assertTrue(error.getMessage().contains("Kit Piloto"));

        service.agregar(comprador, "kit-piloto", 2);
        CarritoDto carrito = service.agregar(comprador, "pantalla-oled", 2);
        assertEquals(2, cantidad(carrito, "pantalla-oled"));
        assertEquals(350 * 2 + 150 * 2, carrito.total());

        // Más accesorios que kits no se permite
        assertThrows(CarritoException.class, () -> service.agregar(comprador, "pantalla-oled", 1));
    }

    @Test
    void quitarKitAjustaLosAccesorios() {
        service.agregar(comprador, "kit-piloto", 2);
        service.agregar(comprador, "bateria-respaldo", 2);
        service.agregar(comprador, "garantia-12m", 1);

        CarritoDto unKit = service.cambiarCantidad(comprador, "kit-piloto", 1);
        assertEquals(1, cantidad(unKit, "bateria-respaldo"));
        assertFalse(unKit.avisos().isEmpty());

        CarritoDto sinKit = service.cambiarCantidad(comprador, "kit-piloto", 0);
        assertTrue(sinKit.lineas().isEmpty());
    }

    @Test
    void soloUnPlanALaVez() {
        service.agregar(comprador, "plan-free", 1);
        CarritoDto carrito = service.agregar(comprador, "plan-standard", 3);

        assertEquals(1, carrito.lineas().size());
        assertEquals("plan-standard", carrito.lineas().get(0).productoId());
        assertEquals(1, carrito.lineas().get(0).cantidad());
        assertEquals(499, carrito.total());
    }

    @Test
    void crearPedidoQuedaPendienteYNoVaciaElCarrito() {
        assertThrows(CarritoException.class, () -> service.crearPedido(comprador));

        service.agregar(comprador, "kit-piloto", 1);
        service.agregar(comprador, "carcasa-personalizada", 1);
        service.agregar(comprador, "plan-standard", 1);

        Pedido pedido = service.crearPedido(comprador);

        assertEquals(Pedido.PENDIENTE, pedido.getEstado());
        assertEquals(350 + 180 + 499, pedido.getTotal());
        assertEquals(3, pedido.getLineas().size());
        // El carrito se vacía hasta que PayPal confirma el pago
        assertEquals(3, service.ver(comprador).lineas().size());
    }

    @Test
    void soloLosKitsPagadosCuentanParaAccesorios() {
        pedidos.add(new Pedido("FLX-P0", "u1",
                List.of(new Pedido.Linea("kit-piloto", "Kit Piloto FluxGuard", 350, 1, 350)),
                350, Pedido.PENDIENTE, LocalDateTime.now()));

        assertThrows(CarritoException.class, () -> service.agregar(comprador, "garantia-12m", 1));
    }

    @Test
    void kitCompradoAntesPermiteAccesoriosSinKitEnCarrito() {
        pedidos.add(new Pedido("FLX-P1", "u1",
                List.of(new Pedido.Linea("kit-piloto", "Kit Piloto FluxGuard", 350, 1, 350)),
                350, Pedido.PAGADO, LocalDateTime.now()));

        CarritoDto carrito = service.agregar(comprador, "garantia-12m", 1);
        assertEquals(250, carrito.total());
        assertThrows(CarritoException.class, () -> service.agregar(comprador, "garantia-12m", 1));
    }

    @Test
    void productoInexistenteOCantidadInvalida() {
        assertThrows(CarritoException.class, () -> service.agregar(comprador, "no-existe", 1));
        assertThrows(CarritoException.class, () -> service.agregar(comprador, "kit-piloto", 0));
        assertThrows(CarritoException.class, () -> service.agregar(comprador, "kit-piloto", 11));
    }

    @Test
    void invitadoCompraHardwarePeroNoPlanes() {
        Comprador invitado = Comprador.invitado("token-de-invitado-0123456789");

        CarritoException error = assertThrows(CarritoException.class, () -> service.agregar(invitado, "plan-standard", 1));
        assertTrue(error.getMessage().contains("iniciar sesión"));

        service.agregar(invitado, "kit-piloto", 1);
        service.agregar(invitado, "pantalla-oled", 1);
        Pedido pedido = service.crearPedido(invitado);
        assertEquals(350 + 150, pedido.getTotal());
        assertTrue(pedido.getUsuarioId().startsWith("invitado:"));
        assertNull(pedido.getCorreoCliente());
    }

    @Test
    void alIniciarSesionElCarritoDeInvitadoPasaALaCuenta() {
        Comprador invitado = Comprador.invitado("token-de-invitado-0123456789");
        service.agregar(invitado, "kit-piloto", 1);
        service.agregar(invitado, "pantalla-oled", 1);
        service.agregar(comprador, "plan-standard", 1);

        CarritoDto carrito = service.fusionar(invitado, comprador);

        assertEquals(350 + 150 + 499, carrito.total());
        assertTrue(service.ver(invitado).lineas().isEmpty());
    }
}
