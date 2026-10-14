package com.fluxguard.service;

import com.fluxguard.dto.CarritoDto;
import com.fluxguard.model.Carrito;
import com.fluxguard.model.Pedido;
import com.fluxguard.model.Producto;
import com.fluxguard.model.Producto.Categoria;
import com.fluxguard.model.Usuario;
import com.fluxguard.repository.CarritoRepository;
import com.fluxguard.repository.PedidoRepository;
import com.fluxguard.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Reglas del carrito:
 * - Solo un plan a la vez (agregar otro lo reemplaza) y siempre con cantidad 1.
 * - Personalizaciones y garantía requieren un kit piloto en el carrito o comprado antes,
 *   y se permite como máximo una de cada una por kit.
 * - Los precios siempre salen del catálogo, nunca del navegador.
 */
@Service
public class CarritoService {

    /** Error de negocio con un mensaje que se puede mostrar al usuario. */
    public static class CarritoException extends RuntimeException {
        public CarritoException(String mensaje) {
            super(mensaje);
        }
    }

    public record Compra(Pedido pedido, Usuario usuario) {}

    private final CarritoRepository carritoRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CatalogoService catalogo;
    private final SuscripcionService suscripcionService;
    private final SecureRandom random = new SecureRandom();

    public CarritoService(CarritoRepository carritoRepository, PedidoRepository pedidoRepository,
                          UsuarioRepository usuarioRepository, CatalogoService catalogo,
                          SuscripcionService suscripcionService) {
        this.carritoRepository = carritoRepository;
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.catalogo = catalogo;
        this.suscripcionService = suscripcionService;
    }

    public CarritoDto ver(Usuario usuario) {
        Carrito carrito = obtener(usuario);
        int kitsComprados = kitsComprados(usuario);
        List<String> avisos = normalizar(carrito, kitsComprados);
        if (!avisos.isEmpty()) {
            guardar(carrito);
        }
        return aDto(carrito, kitsComprados, avisos);
    }

    public CarritoDto agregar(Usuario usuario, String productoId, int cantidad) {
        Producto producto = catalogo.buscar(productoId)
                .orElseThrow(() -> new CarritoException("El producto no existe."));
        if (cantidad < 1) {
            throw new CarritoException("La cantidad debe ser al menos 1.");
        }

        Carrito carrito = obtener(usuario);
        int kitsComprados = kitsComprados(usuario);

        if (producto.categoria() == Categoria.PLAN) {
            // Un solo plan: se reemplaza el que hubiera
            carrito.getItems().removeIf(item -> esPlan(item.getProductoId()));
            carrito.getItems().add(new Carrito.Item(producto.id(), 1));
        } else {
            Optional<Carrito.Item> existente = buscarItem(carrito, producto.id());
            int nueva = existente.map(Carrito.Item::getCantidad).orElse(0) + cantidad;
            validarCantidad(carrito, producto, nueva, kitsComprados);
            if (existente.isPresent()) {
                existente.get().setCantidad(nueva);
            } else {
                carrito.getItems().add(new Carrito.Item(producto.id(), nueva));
            }
        }

        guardar(carrito);
        return aDto(carrito, kitsComprados, List.of());
    }

    /** Cambia la cantidad de un producto del carrito. Con cantidad 0 lo quita. */
    public CarritoDto cambiarCantidad(Usuario usuario, String productoId, int cantidad) {
        Carrito carrito = obtener(usuario);
        int kitsComprados = kitsComprados(usuario);
        Carrito.Item item = buscarItem(carrito, productoId)
                .orElseThrow(() -> new CarritoException("Ese producto no está en tu carrito."));

        if (cantidad <= 0) {
            carrito.getItems().remove(item);
        } else {
            Producto producto = catalogo.buscar(productoId)
                    .orElseThrow(() -> new CarritoException("El producto no existe."));
            validarCantidad(carrito, producto, cantidad, kitsComprados);
            item.setCantidad(cantidad);
        }

        // Si se redujeron los kits, los accesorios se ajustan solos
        List<String> avisos = normalizar(carrito, kitsComprados);
        guardar(carrito);
        return aDto(carrito, kitsComprados, avisos);
    }

    /** Convierte el carrito en un pedido, activa el plan si hay uno y vacía el carrito. */
    public Compra comprar(Usuario usuario) {
        Carrito carrito = obtener(usuario);
        int kitsComprados = kitsComprados(usuario);
        List<String> avisos = normalizar(carrito, kitsComprados);
        if (!avisos.isEmpty()) {
            guardar(carrito);
            throw new CarritoException("Tu carrito cambió: " + String.join(" ", avisos) + " Revísalo antes de pagar.");
        }
        if (carrito.getItems().isEmpty()) {
            throw new CarritoException("Tu carrito está vacío.");
        }

        List<Pedido.Linea> lineas = new ArrayList<>();
        int total = 0;
        Producto plan = null;
        boolean incluyeKit = false;
        for (Carrito.Item item : carrito.getItems()) {
            Producto producto = catalogo.buscar(item.getProductoId()).orElseThrow();
            int importe = producto.precio() * item.getCantidad();
            lineas.add(new Pedido.Linea(producto.id(), producto.nombre(), producto.precio(), item.getCantidad(), importe));
            total += importe;
            if (producto.categoria() == Categoria.PLAN) {
                plan = producto;
            }
            if (producto.id().equals(CatalogoService.KIT_PILOTO)) {
                incluyeKit = true;
            }
        }

        // Pago simulado: falta integrar una pasarela de pago real
        Pedido pedido = pedidoRepository.save(new Pedido(
                "FLX-P" + (10000000 + random.nextInt(90000000)), usuario.getId(), lineas, total,
                "Pagado (simulado)", LocalDateTime.now()));

        if (plan != null) {
            suscripcionService.activar(usuario, CatalogoService.tipoDePlan(plan));
        } else if (incluyeKit && usuario.getSuscripcion() == null) {
            // Igual que en pago.html: el kit piloto incluye el dashboard
            suscripcionService.activar(usuario, "prototipo");
        }
        Usuario guardado = usuarioRepository.save(usuario);

        carrito.getItems().clear();
        guardar(carrito);
        return new Compra(pedido, guardado);
    }

    private void validarCantidad(Carrito carrito, Producto producto, int cantidad, int kitsComprados) {
        if (cantidad > producto.maxCantidad()) {
            throw new CarritoException("Puedes pedir hasta " + producto.maxCantidad() + " de " + producto.nombre() + ".");
        }
        if (producto.requiereKit()) {
            int kits = kitsDisponibles(carrito, kitsComprados);
            if (kits == 0) {
                throw new CarritoException(producto.nombre() + " es para el Kit Piloto. Agrega primero un Kit Piloto al carrito.");
            }
            if (cantidad > kits) {
                throw new CarritoException("Solo puedes agregar " + kits + " de " + producto.nombre() + " (una por kit).");
            }
        }
    }

    /** Quita productos que ya no existen y ajusta los accesorios al número de kits. Devuelve qué cambió. */
    private List<String> normalizar(Carrito carrito, int kitsComprados) {
        List<String> avisos = new ArrayList<>();
        int kits = kitsDisponibles(carrito, kitsComprados);
        Iterator<Carrito.Item> it = carrito.getItems().iterator();
        while (it.hasNext()) {
            Carrito.Item item = it.next();
            Optional<Producto> producto = catalogo.buscar(item.getProductoId());
            if (producto.isEmpty()) {
                it.remove();
                avisos.add("Se quitó un producto que ya no está disponible.");
                continue;
            }
            Producto p = producto.get();
            int maximo = p.requiereKit() ? Math.min(p.maxCantidad(), kits) : p.maxCantidad();
            if (maximo == 0) {
                it.remove();
                avisos.add("Se quitó " + p.nombre() + " porque requiere un Kit Piloto.");
            } else if (item.getCantidad() > maximo) {
                item.setCantidad(maximo);
                avisos.add("Se ajustó " + p.nombre() + " a " + maximo + " (una por kit).");
            } else if (item.getCantidad() < 1) {
                it.remove();
            }
        }
        return avisos;
    }

    private CarritoDto aDto(Carrito carrito, int kitsComprados, List<String> avisos) {
        int kits = kitsDisponibles(carrito, kitsComprados);
        List<CarritoDto.Linea> lineas = new ArrayList<>();
        int total = 0;
        int articulos = 0;
        for (Carrito.Item item : carrito.getItems()) {
            Producto p = catalogo.buscar(item.getProductoId()).orElseThrow();
            int importe = p.precio() * item.getCantidad();
            int maximo = p.requiereKit() ? Math.min(p.maxCantidad(), kits) : p.maxCantidad();
            lineas.add(new CarritoDto.Linea(p.id(), p.nombre(), p.categoria().name(), p.icono(), p.unidad(),
                    p.precio(), item.getCantidad(), importe, maximo));
            total += importe;
            articulos += item.getCantidad();
        }
        return new CarritoDto(lineas, total, articulos, avisos);
    }

    private Carrito obtener(Usuario usuario) {
        return carritoRepository.findByUsuarioId(usuario.getId()).orElseGet(() -> new Carrito(usuario.getId()));
    }

    private void guardar(Carrito carrito) {
        carrito.setActualizado(LocalDateTime.now());
        carritoRepository.save(carrito);
    }

    private int kitsDisponibles(Carrito carrito, int kitsComprados) {
        return kitsComprados + buscarItem(carrito, CatalogoService.KIT_PILOTO).map(Carrito.Item::getCantidad).orElse(0);
    }

    private int kitsComprados(Usuario usuario) {
        return pedidoRepository.findByUsuarioIdOrderByFechaDesc(usuario.getId()).stream()
                .flatMap(pedido -> pedido.getLineas().stream())
                .filter(linea -> CatalogoService.KIT_PILOTO.equals(linea.productoId()))
                .mapToInt(Pedido.Linea::cantidad)
                .sum();
    }

    private boolean esPlan(String productoId) {
        return catalogo.buscar(productoId).map(p -> p.categoria() == Categoria.PLAN).orElse(false);
    }

    private static Optional<Carrito.Item> buscarItem(Carrito carrito, String productoId) {
        return carrito.getItems().stream().filter(item -> item.getProductoId().equals(productoId)).findFirst();
    }
}
