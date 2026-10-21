package com.fluxguard.service;

import com.fluxguard.dto.CarritoDto;
import com.fluxguard.model.Carrito;
import com.fluxguard.model.Pedido;
import com.fluxguard.model.Producto;
import com.fluxguard.model.Producto.Categoria;
import com.fluxguard.repository.CarritoRepository;
import com.fluxguard.repository.PedidoRepository;
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
 * - Personalizaciones y garantía requieren un kit piloto en el carrito o comprado (y pagado) antes,
 *   y se permite como máximo una de cada una por kit.
 * - Los precios siempre salen del catálogo, nunca del navegador.
 * - No se puede pedir más de lo que hay en stock; un producto agotado no se puede agregar.
 * - Los invitados (sin sesión) pueden comprar hardware y servicios, pero no planes: un plan se activa en una cuenta.
 */
@Service
public class CarritoService {

    /** Error de negocio con un mensaje que se puede mostrar al usuario. */
    public static class CarritoException extends RuntimeException {
        public CarritoException(String mensaje) {
            super(mensaje);
        }
    }

    private final CarritoRepository carritoRepository;
    private final PedidoRepository pedidoRepository;
    private final CatalogoService catalogo;
    private final SecureRandom random = new SecureRandom();

    public CarritoService(CarritoRepository carritoRepository, PedidoRepository pedidoRepository, CatalogoService catalogo) {
        this.carritoRepository = carritoRepository;
        this.pedidoRepository = pedidoRepository;
        this.catalogo = catalogo;
    }

    private static final String PLAN_REQUIERE_CUENTA =
            "Para comprar un plan necesitas iniciar sesión: el plan se activa en tu cuenta.";

    public CarritoDto ver(Comprador comprador) {
        Carrito carrito = obtener(comprador);
        int kitsComprados = kitsComprados(comprador);
        List<String> avisos = normalizar(carrito, kitsComprados);
        if (!avisos.isEmpty()) {
            guardar(carrito);
        }
        return aDto(carrito, kitsComprados, avisos);
    }

    public CarritoDto agregar(Comprador comprador, String productoId, int cantidad) {
        Producto producto = catalogo.buscar(productoId)
                .orElseThrow(() -> new CarritoException("El producto no existe."));
        if (cantidad < 1) {
            throw new CarritoException("La cantidad debe ser al menos 1.");
        }

        if (producto.categoria() == Categoria.PLAN && comprador.esInvitado()) {
            throw new CarritoException(PLAN_REQUIERE_CUENTA);
        }

        Carrito carrito = obtener(comprador);
        int kitsComprados = kitsComprados(comprador);

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
    public CarritoDto cambiarCantidad(Comprador comprador, String productoId, int cantidad) {
        Carrito carrito = obtener(comprador);
        int kitsComprados = kitsComprados(comprador);
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

    /**
     * Crea un pedido PENDIENTE con el contenido actual del carrito (precios del catálogo).
     * El carrito no se toca: se vacía cuando el pago se confirma (PagoService).
     */
    public Pedido crearPedido(Comprador comprador) {
        Carrito carrito = obtener(comprador);
        int kitsComprados = kitsComprados(comprador);
        List<String> avisos = normalizar(carrito, kitsComprados);
        if (!avisos.isEmpty()) {
            guardar(carrito);
            throw new CarritoException("Tu carrito cambió: " + String.join(" ", avisos) + " Revísalo antes de pagar.");
        }
        if (carrito.getItems().isEmpty()) {
            throw new CarritoException("Tu carrito está vacío.");
        }
        if (comprador.esInvitado() && carrito.getItems().stream().anyMatch(item -> esPlan(item.getProductoId()))) {
            throw new CarritoException(PLAN_REQUIERE_CUENTA);
        }

        List<Pedido.Linea> lineas = new ArrayList<>();
        int total = 0;
        for (Carrito.Item item : carrito.getItems()) {
            Producto producto = catalogo.buscar(item.getProductoId()).orElseThrow();
            int importe = producto.precio() * item.getCantidad();
            lineas.add(new Pedido.Linea(producto.id(), producto.nombre(), producto.precio(), item.getCantidad(), importe));
            total += importe;
        }

        Pedido pedido = new Pedido("FLX-P" + (10000000 + random.nextInt(90000000)), comprador.clave(), lineas, total,
                Pedido.PENDIENTE, LocalDateTime.now());
        if (!comprador.esInvitado()) {
            // Los datos del invitado se toman de su cuenta de PayPal al cobrar
            pedido.setNombreCliente((comprador.usuario().getNombre() + " " + comprador.usuario().getApellido()).trim());
            pedido.setCorreoCliente(comprador.usuario().getCorreo());
        }
        return pedidoRepository.save(pedido);
    }

    /**
     * Pasa a la cuenta lo que el usuario agregó como invitado antes de iniciar sesión.
     * Lo que no cabe (p. ej. accesorios de más) se omite; el carrito de invitado queda vacío.
     */
    public CarritoDto fusionar(Comprador invitado, Comprador usuario) {
        List<String> avisos = new ArrayList<>();
        carritoRepository.findByUsuarioId(invitado.clave()).ifPresent(carritoInvitado -> {
            // Primero los kits, para que los accesorios tengan a qué asociarse
            List<Carrito.Item> items = new ArrayList<>(carritoInvitado.getItems());
            items.sort((a, b) -> Boolean.compare(!CatalogoService.KIT_PILOTO.equals(a.getProductoId()),
                    !CatalogoService.KIT_PILOTO.equals(b.getProductoId())));
            for (Carrito.Item item : items) {
                try {
                    agregar(usuario, item.getProductoId(), item.getCantidad());
                } catch (CarritoException e) {
                    avisos.add(e.getMessage());
                }
            }
            carritoInvitado.getItems().clear();
            guardar(carritoInvitado);
        });
        CarritoDto carrito = ver(usuario);
        avisos.addAll(carrito.avisos());
        return new CarritoDto(carrito.lineas(), carrito.total(), carrito.cantidadArticulos(), avisos);
    }

    public void vaciar(String usuarioId) {
        carritoRepository.findByUsuarioId(usuarioId).ifPresent(carrito -> {
            carrito.getItems().clear();
            guardar(carrito);
        });
    }

    private void validarCantidad(Carrito carrito, Producto producto, int cantidad, int kitsComprados) {
        if (producto.agotado()) {
            throw new CarritoException(producto.nombre() + " está agotado por el momento.");
        }
        if (producto.stock() != null && cantidad > producto.stock()) {
            throw new CarritoException("Solo quedan " + producto.stock() + " unidades de " + producto.nombre() + ".");
        }
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
            int maximo = maximo(p, kits);
            if (p.agotado()) {
                it.remove();
                avisos.add("Se quitó " + p.nombre() + " porque se agotó.");
            } else if (maximo == 0) {
                it.remove();
                avisos.add("Se quitó " + p.nombre() + " porque requiere un Kit Piloto.");
            } else if (item.getCantidad() > maximo) {
                item.setCantidad(maximo);
                boolean porStock = p.stock() != null && maximo == p.stock();
                avisos.add(porStock
                        ?"Se ajustó " + p.nombre() + " a " + maximo + " (las unidades que quedan en stock)."
                        : "Se ajustó " + p.nombre() + " a " + maximo + " (una por kit).");
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
            int maximo = maximo(p, kits);
            lineas.add(new CarritoDto.Linea(p.id(), p.nombre(), p.categoria().name(), p.icono(), p.unidad(),
                    p.precio(), item.getCantidad(), importe, maximo));
            total += importe;
            articulos += item.getCantidad();
        }
        return new CarritoDto(lineas, total, articulos, avisos);
    }

    /** Máximo que se puede tener en el carrito de un producto: límite por pedido, stock y, en accesorios, uno por kit. */
    private static int maximo(Producto p, int kits) {
        return p.requiereKit() ? Math.min(p.maximoPedible(), kits) : p.maximoPedible();
    }

    private Carrito obtener(Comprador comprador) {
        return carritoRepository.findByUsuarioId(comprador.clave()).orElseGet(() -> new Carrito(comprador.clave()));
    }

    private void guardar(Carrito carrito) {
        carrito.setActualizado(LocalDateTime.now());
        carritoRepository.save(carrito);
    }

    private int kitsDisponibles(Carrito carrito, int kitsComprados) {
        return kitsComprados + buscarItem(carrito, CatalogoService.KIT_PILOTO).map(Carrito.Item::getCantidad).orElse(0);
    }

    private int kitsComprados(Comprador comprador) {
        return pedidoRepository.findByUsuarioIdOrderByFechaDesc(comprador.clave()).stream()
                .filter(Pedido::estaPagado)
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
