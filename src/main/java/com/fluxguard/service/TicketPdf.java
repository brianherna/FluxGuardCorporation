package com.fluxguard.service;

import com.fluxguard.model.Pedido;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF del ticket de compra, con el mismo contenido que el ticket impreso (main.js) y el del correo (PlantillaTicket).
 * Formato de ticket de 80 mm de ancho; el alto se ajusta al número de productos.
 */
public final class TicketPdf {

    private static final float ANCHO = 80 / 25.4f * 72;   // 80 mm en puntos
    private static final float MARGEN = 14;
    private static final float CONTENIDO = ANCHO - 2 * MARGEN;

    private static final PDFont MARCA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont TEXTO = new PDType1Font(Standard14Fonts.FontName.COURIER);
    private static final PDFont NEGRITA = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);

    private enum Alineacion { IZQUIERDA, CENTRO, DERECHA }

    /** Un renglón del ticket: texto simple, dos columnas (etiqueta y valor), separador punteado o espacio. */
    private record Renglon(String izquierda, String derecha, PDFont fuente, float tamano, Alineacion alineacion,
                           boolean separador, float espacio) {
        float alto() {
            return separador ? 10 : espacio > 0 ? espacio : tamano * 1.45f;
        }
    }

    private TicketPdf() {}

    public static String nombreArchivo(Pedido pedido) {
        return "ticket-" + pedido.getFolio() + ".pdf";
    }

    public static byte[] generar(Pedido pedido) {
        List<Renglon> renglones = renglones(pedido);
        float alto = 2 * MARGEN;
        for (Renglon r : renglones) {
            alto += r.alto();
        }

        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDDocumentInformation info = documento.getDocumentInformation();
            info.setTitle("Ticket de compra " + pedido.getFolio());
            info.setAuthor("FluxGuard");

            PDPage pagina = new PDPage(new PDRectangle(ANCHO, alto));
            documento.addPage(pagina);
            try (PDPageContentStream lienzo = new PDPageContentStream(documento, pagina)) {
                float y = alto - MARGEN;
                for (Renglon r : renglones) {
                    y -= r.alto();
                    dibujar(lienzo, r, y);
                }
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del ticket " + pedido.getFolio(), e);
        }
    }

    private static List<Renglon> renglones(Pedido pedido) {
        List<Renglon> r = new ArrayList<>();
        centrado(r, "FLUXGUARD", MARCA, 16);
        espacio(r, 4);
        centrado(r, PlantillaTicket.DOMICILIO, TEXTO, 7);
        centrado(r, PlantillaTicket.CORREO_CONTACTO, TEXTO, 7);
        separador(r);
        centrado(r, "TICKET DE COMPRA", NEGRITA, 9);
        separador(r);
        fila(r, "Folio", pedido.getFolio(), TEXTO, 8);
        fila(r, "Fecha", PlantillaTicket.fecha(pedido), TEXTO, 8);
        fila(r, "Cliente", pedido.getNombreCliente(), TEXTO, 8);
        fila(r, "Correo", pedido.getCorreoCliente(), TEXTO, 8);
        fila(r, "Pago", PlantillaTicket.metodo(pedido), TEXTO, 8);
        fila(r, "Referencia", pedido.getPagoId(), TEXTO, 8);
        separador(r);
        for (Pedido.Linea linea : pedido.getLineas()) {
            for (String parte : partir(linea.nombre(), TEXTO, 8, CONTENIDO)) {
                r.add(new Renglon(parte, null, TEXTO, 8, Alineacion.IZQUIERDA, false, 0));
            }
            fila(r, linea.cantidad() + " x " + PlantillaTicket.dinero(linea.precioUnitario()),
                    PlantillaTicket.dinero(linea.importe()), TEXTO, 8);
            espacio(r, 3);
        }
        separador(r);
        BigDecimal iva = PlantillaTicket.iva(pedido.getTotal());
        fila(r, "Subtotal", PlantillaTicket.dinero(BigDecimal.valueOf(pedido.getTotal()).subtract(iva)), TEXTO, 8);
        fila(r, "IVA (16 %)", PlantillaTicket.dinero(iva), TEXTO, 8);
        fila(r, "TOTAL", PlantillaTicket.dinero(pedido.getTotal()), NEGRITA, 11);
        separador(r);
        centrado(r, "¡Gracias por tu compra!", TEXTO, 8);
        centrado(r, "Conserva tu folio para garantías y devoluciones.", TEXTO, 7);
        espacio(r, 4);
        centrado(r, "Este ticket no es un comprobante fiscal (CFDI). Si necesitas factura, escríbenos con tu folio.", TEXTO, 6.5f);
        return r;
    }

    private static void centrado(List<Renglon> r, String texto, PDFont fuente, float tamano) {
        for (String parte : partir(texto, fuente, tamano, CONTENIDO)) {
            r.add(new Renglon(parte, null, fuente, tamano, Alineacion.CENTRO, false, 0));
        }
    }

    /** Etiqueta a la izquierda y valor a la derecha; si no caben juntos, el valor baja al siguiente renglón. */
    private static void fila(List<Renglon> r, String etiqueta, String valor, PDFont fuente, float tamano) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        float necesario = ancho(etiqueta, fuente, tamano) + 8 + ancho(valor, fuente, tamano);
        if (necesario <= CONTENIDO) {
            r.add(new Renglon(etiqueta, valor, fuente, tamano, Alineacion.IZQUIERDA, false, 0));
            return;
        }
        r.add(new Renglon(etiqueta, null, fuente, tamano, Alineacion.IZQUIERDA, false, 0));
        for (String parte : partir(valor, fuente, tamano, CONTENIDO)) {
            r.add(new Renglon(parte, null, fuente, tamano, Alineacion.DERECHA, false, 0));
        }
    }

    private static void separador(List<Renglon> r) {
        r.add(new Renglon(null, null, TEXTO, 0, Alineacion.IZQUIERDA, true, 0));
    }

    private static void espacio(List<Renglon> r, float puntos) {
        r.add(new Renglon(null, null, TEXTO, 0, Alineacion.IZQUIERDA, false, puntos));
    }

    private static void dibujar(PDPageContentStream lienzo, Renglon r, float y) throws IOException {
        if (r.separador()) {
            lienzo.setLineDashPattern(new float[]{2, 2}, 0);
            lienzo.setLineWidth(0.6f);
            lienzo.moveTo(MARGEN, y + 5);
            lienzo.lineTo(ANCHO - MARGEN, y + 5);
            lienzo.stroke();
            return;
        }
        if (r.izquierda() == null) {
            return;
        }
        float base = y + r.tamano() * 0.35f;
        String izquierda = limpiar(r.izquierda(), r.fuente());
        float x = switch (r.alineacion()) {
            case CENTRO -> MARGEN + (CONTENIDO - ancho(izquierda, r.fuente(), r.tamano())) / 2;
            case DERECHA -> ANCHO - MARGEN - ancho(izquierda, r.fuente(), r.tamano());
            case IZQUIERDA -> MARGEN;
        };
        texto(lienzo, izquierda, r.fuente(), r.tamano(), x, base);
        if (r.derecha() != null) {
            String derecha = limpiar(r.derecha(), r.fuente());
            texto(lienzo, derecha, r.fuente(), r.tamano(), ANCHO - MARGEN - ancho(derecha, r.fuente(), r.tamano()), base);
        }
    }

    private static void texto(PDPageContentStream lienzo, String texto, PDFont fuente, float tamano, float x, float y) throws IOException {
        lienzo.beginText();
        lienzo.setFont(fuente, tamano);
        lienzo.newLineAtOffset(x, y);
        lienzo.showText(texto);
        lienzo.endText();
    }

    /** Parte un texto en renglones que quepan en el ancho dado: por palabras, y por letras si una palabra no cabe sola (p. ej. un correo largo). */
    private static List<String> partir(String texto, PDFont fuente, float tamano, float maximo) {
        List<String> palabras = new ArrayList<>();
        for (String palabra : limpiar(texto, fuente).split("\\s+")) {
            while (ancho(palabra, fuente, tamano) > maximo && palabra.length() > 1) {
                int corte = palabra.length() - 1;
                while (corte > 1 && ancho(palabra.substring(0, corte), fuente, tamano) > maximo) {
                    corte--;
                }
                palabras.add(palabra.substring(0, corte));
                palabra = palabra.substring(corte);
            }
            palabras.add(palabra);
        }

        List<String> partes = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : palabras) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (ancho(prueba, fuente, tamano) <= maximo || actual.isEmpty()) {
                actual = new StringBuilder(prueba);
            } else {
                partes.add(actual.toString());
                actual = new StringBuilder(palabra);
            }
        }
        if (!actual.isEmpty()) {
            partes.add(actual.toString());
        }
        return partes;
    }

    private static float ancho(String texto, PDFont fuente, float tamano) {
        try {
            return fuente.getStringWidth(limpiar(texto, fuente)) / 1000 * tamano;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Las fuentes estándar del PDF solo tienen caracteres latinos (WinAnsi): lo que no exista se cambia por "?". */
    static String limpiar(String texto, PDFont fuente) {
        if (texto == null) {
            return "";
        }
        StringBuilder limpio = new StringBuilder();
        texto.codePoints().forEach(c -> {
            String caracter = new String(Character.toChars(c));
            try {
                fuente.encode(caracter);
                limpio.append(caracter);
            } catch (IllegalArgumentException | IOException e) {
                limpio.append(Character.isWhitespace(c) ? " " : "?");
            }
        });
        return limpio.toString();
    }
}
