package com.fluxguard;

import com.fluxguard.model.DispositivoPush;
import com.fluxguard.model.Notificacion;
import com.fluxguard.model.Pedido;
import com.fluxguard.repository.DispositivoPushRepository;
import com.fluxguard.repository.NotificacionRepository;
import com.fluxguard.service.FcmService;
import com.fluxguard.service.FcmService.FcmException;
import com.fluxguard.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificacionServiceTests {

    private NotificacionRepository notificaciones;
    private DispositivoPushRepository dispositivos;
    private FcmService fcm;
    private NotificacionService servicio;

    @BeforeEach
    void setUp() {
        notificaciones = mock(NotificacionRepository.class);
        dispositivos = mock(DispositivoPushRepository.class);
        fcm = mock(FcmService.class);
        when(notificaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fcm.estaConfigurado()).thenReturn(true);
        servicio = new NotificacionService(notificaciones, dispositivos, fcm);
    }

    private Pedido pedido(String usuarioId, String dispositivo) {
        Pedido pedido = new Pedido("FLX-100", usuarioId, List.of(), 350, Pedido.PAGADO, LocalDateTime.now());
        pedido.setDispositivo(dispositivo);
        return pedido;
    }

    private DispositivoPush celular(String token) {
        return new DispositivoPush("u1", token, "android", LocalDateTime.now());
    }

    @Test
    void laCompraGuardaElAvisoYDiceDesdeDondeSeHizo() {
        servicio.avisarCompra(pedido("u1", NotificacionService.ORIGEN_PC));

        ArgumentCaptor<Notificacion> aviso = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(aviso.capture());
        assertEquals("compra-FLX-100", aviso.getValue().getId());
        assertEquals("u1", aviso.getValue().getUsuarioId());
        assertEquals("Alguien compró en tu cuenta", aviso.getValue().getTitulo());
        assertTrue(aviso.getValue().getMensaje().contains("FLX-100"));
        assertTrue(aviso.getValue().getMensaje().contains("$350 MXN desde una PC"));
        assertFalse(aviso.getValue().isLeida());
    }

    @Test
    void sinDispositivoConocidoElMensajeNoInventaElOrigen() {
        servicio.avisarCompra(pedido("u1", null));

        ArgumentCaptor<Notificacion> aviso = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(aviso.capture());
        assertFalse(aviso.getValue().getMensaje().contains("desde"));
    }

    @Test
    void elPushLlegaATodosLosCelularesDeLaCuenta() {
        when(dispositivos.findByUsuarioId("u1")).thenReturn(List.of(celular("t1"), celular("t2")));
        when(fcm.enviar(anyString(), anyString(), anyString(), anyMap())).thenReturn(true);

        servicio.avisarCompra(pedido("u1", NotificacionService.ORIGEN_CELULAR));

        verify(fcm).enviar(eq("t1"), eq("Alguien compró en tu cuenta"), contains("desde un celular"), anyMap());
        verify(fcm).enviar(eq("t2"), anyString(), anyString(), anyMap());
    }

    @Test
    void unMismoPedidoNoAvisaDosVeces() {
        when(notificaciones.existsById("compra-FLX-100")).thenReturn(true);

        servicio.avisarCompra(pedido("u1", NotificacionService.ORIGEN_PC));

        verify(notificaciones, never()).save(any());
        verify(fcm, never()).enviar(any(), any(), any(), any());
    }

    @Test
    void losInvitadosNoTienenCuentaQueAvisar() {
        servicio.avisarCompra(pedido("invitado:abc", NotificacionService.ORIGEN_PC));

        verifyNoInteractions(notificaciones, fcm);
    }

    @Test
    void sinFirebaseElAvisoSeGuardaIgual() {
        when(fcm.estaConfigurado()).thenReturn(false);

        servicio.avisarCompra(pedido("u1", NotificacionService.ORIGEN_PC));

        verify(notificaciones).save(any());
        verify(fcm, never()).enviar(any(), any(), any(), any());
    }

    @Test
    void unTokenQueYaNoExisteSeOlvida() {
        when(dispositivos.findByUsuarioId("u1")).thenReturn(List.of(celular("viejo")));
        when(fcm.enviar(eq("viejo"), anyString(), anyString(), anyMap())).thenReturn(false);

        servicio.avisarCompra(pedido("u1", NotificacionService.ORIGEN_PC));

        verify(dispositivos).deleteByToken("viejo");
    }

    @Test
    void unCelularQueFallaNoDejaSinAvisoALosDemas() {
        when(dispositivos.findByUsuarioId("u1")).thenReturn(List.of(celular("malo"), celular("bueno")));
        when(fcm.enviar(eq("malo"), anyString(), anyString(), anyMap())).thenThrow(new FcmException("caído", null));
        when(fcm.enviar(eq("bueno"), anyString(), anyString(), anyMap())).thenReturn(true);

        servicio.avisarCompra(pedido("u1", NotificacionService.ORIGEN_PC));

        verify(fcm).enviar(eq("bueno"), anyString(), anyString(), anyMap());
        verify(dispositivos, never()).deleteByToken(anyString());
    }

    @Test
    void unErrorAlAvisarNoRompeElPago() {
        when(notificaciones.save(any())).thenThrow(new RuntimeException("sin base de datos"));

        assertDoesNotThrow(() -> servicio.avisarCompra(pedido("u1", NotificacionService.ORIGEN_PC)));
    }

    @Test
    void marcarLeidasSoloTocaLasDeEseUsuario() {
        Notificacion aviso = new Notificacion("compra-FLX-100", "u1", Notificacion.COMPRA, "t", "m", "FLX-100", LocalDateTime.now());
        when(notificaciones.findByUsuarioIdAndLeidaFalse("u1")).thenReturn(List.of(aviso));

        servicio.marcarLeidas("u1");

        assertTrue(aviso.isLeida());
        verify(notificaciones).saveAll(List.of(aviso));
    }

    @Test
    void siOtraCuentaIniciaSesionEnElCelularElTokenPasaAEsaCuenta() {
        when(dispositivos.findByToken("t1")).thenReturn(Optional.of(celular("t1")));

        servicio.registrarDispositivo("u2", "t1", "android");

        ArgumentCaptor<DispositivoPush> guardado = ArgumentCaptor.forClass(DispositivoPush.class);
        verify(dispositivos).save(guardado.capture());
        assertEquals("u2", guardado.getValue().getUsuarioId());
    }

    @Test
    void reconoceDesdeDondeSeCompra() {
        String windows = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36";
        String iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";
        String androidCelular = "Mozilla/5.0 (Linux; Android 15; Pixel 8a) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36";
        String androidTablet = "Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36 Chrome/130.0 Safari/537.36";

        assertEquals(NotificacionService.ORIGEN_PC, NotificacionService.origenDesde(windows));
        assertEquals(NotificacionService.ORIGEN_CELULAR, NotificacionService.origenDesde(iphone));
        assertEquals(NotificacionService.ORIGEN_CELULAR, NotificacionService.origenDesde(androidCelular));
        assertEquals(NotificacionService.ORIGEN_TABLET, NotificacionService.origenDesde(androidTablet));
        assertEquals(NotificacionService.ORIGEN_APP, NotificacionService.origenDesde("FluxGuardApp/1.0 (android)"));
        assertNull(NotificacionService.origenDesde(null));
        assertNull(NotificacionService.origenDesde(" "));
    }

    @Test
    void sinCredencialesOConCredencialesMalasFirebaseQuedaApagado() {
        assertFalse(new FcmService("").estaConfigurado());
        assertFalse(new FcmService("esto no es json").estaConfigurado());
    }

    @Test
    void elJwtDeFirebaseVaFirmadoConLaLlaveDeLaCuenta() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        KeyPair llaves = generador.generateKeyPair();

        String[] partes = FcmService.jwt("push@proyecto.iam.gserviceaccount.com", llaves.getPrivate(), 1_000).split("\\.");

        String datos = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        assertTrue(datos.contains("\"iss\":\"push@proyecto.iam.gserviceaccount.com\""));
        assertTrue(datos.contains("\"exp\":4600"));
        Signature firma = Signature.getInstance("SHA256withRSA");
        firma.initVerify(llaves.getPublic());
        firma.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.UTF_8));
        assertTrue(firma.verify(Base64.getUrlDecoder().decode(partes[2])));
    }
}
