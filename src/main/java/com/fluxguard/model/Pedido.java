package com.fluxguard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Compra hecha desde el carrito. Guarda nombre y precio de cada línea tal como estaban al comprar.
 * Nace como PENDIENTE y pasa a PAGADO solo cuando el proveedor (PayPal o Stripe) confirma el cobro.
 * Con pago en tienda (Paynet) queda PENDIENTE con su referencia hasta que el cliente pague.
 */
@Document(collection = "pedidos")
public class Pedido {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String PAGADO = "PAGADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String CANCELADO = "CANCELADO";
    public static final String REEMBOLSADO = "REEMBOLSADO";

    @Id
    private String id;

    @Indexed(unique = true)
    private String folio;

    /** Id del usuario, o "invitado:<hash>" si se compró sin iniciar sesión (ver Comprador). */
    @Indexed
    private String usuarioId;

    private String nombreCliente;
    private String correoCliente;

    private List<Linea> lineas;
    private int total;
    private String estado;
    private LocalDateTime fecha;

    // PayPal: ordenId es la orden creada; pagoId es el id del cobro (capture)
    private String ordenId;
    private String urlPago;
    private String pagoId;
    private String metodoPago;
    private LocalDateTime pagadoEn;

    // Pago en tienda (Paynet): referencia, imagen del código de barras y fecha límite. urlPago guarda la ficha PDF.
    private String referenciaPago;
    private String codigoBarrasUrl;
    private LocalDateTime vencePago;

    public Pedido() {}

    public Pedido(String folio, String usuarioId, List<Linea> lineas, int total, String estado, LocalDateTime fecha) {
        this.folio = folio;
        this.usuarioId = usuarioId;
        this.lineas = lineas;
        this.total = total;
        this.estado = estado;
        this.fecha = fecha;
    }

    public boolean estaPagado() {
        return PAGADO.equals(estado);
    }

    public record Linea(String productoId, String nombre, int precioUnitario, int cantidad, int importe) {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public List<Linea> getLineas() {
        return lineas;
    }

    public void setLineas(List<Linea> lineas) {
        this.lineas = lineas;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public String getOrdenId() {
        return ordenId;
    }

    public void setOrdenId(String ordenId) {
        this.ordenId = ordenId;
    }

    public String getUrlPago() {
        return urlPago;
    }

    public void setUrlPago(String urlPago) {
        this.urlPago = urlPago;
    }

    public String getPagoId() {
        return pagoId;
    }

    public void setPagoId(String pagoId) {
        this.pagoId = pagoId;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public LocalDateTime getPagadoEn() {
        return pagadoEn;
    }

    public void setPagadoEn(LocalDateTime pagadoEn) {
        this.pagadoEn = pagadoEn;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public String getCorreoCliente() {
        return correoCliente;
    }

    public void setCorreoCliente(String correoCliente) {
        this.correoCliente = correoCliente;
    }

    public String getReferenciaPago() {
        return referenciaPago;
    }

    public void setReferenciaPago(String referenciaPago) {
        this.referenciaPago = referenciaPago;
    }

    public String getCodigoBarrasUrl() {
        return codigoBarrasUrl;
    }

    public void setCodigoBarrasUrl(String codigoBarrasUrl) {
        this.codigoBarrasUrl = codigoBarrasUrl;
    }

    public LocalDateTime getVencePago() {
        return vencePago;
    }

    public void setVencePago(LocalDateTime vencePago) {
        this.vencePago = vencePago;
    }
}
