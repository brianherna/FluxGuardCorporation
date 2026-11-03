package com.fluxguard.service;

import com.fluxguard.model.DispositivoPush;
import com.fluxguard.model.Notificacion;
import com.fluxguard.model.Pedido;
import com.fluxguard.repository.DispositivoPushRepository;
import com.fluxguard.repository.NotificacionRepository;
import com.fluxguard.service.FcmService.FcmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Avisos al dueño de la cuenta. Cada compra pagada guarda una notificación (la leen la app y la web)
 * y, si la cuenta tiene celulares registrados y Firebase está configurado, se envía también como push.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private static final String PREFIJO_INVITADO = "invitado:";

    public static final String ORIGEN_APP = "app";
    public static final String ORIGEN_PC = "pc";
    public static final String ORIGEN_CELULAR = "celular";
    public static final String ORIGEN_TABLET = "tablet";

    private final NotificacionRepository notificacionRepository;
    private final DispositivoPushRepository dispositivoRepository;
    private final FcmService fcm;

    public NotificacionService(NotificacionRepository notificacionRepository, DispositivoPushRepository dispositivoRepository, FcmService fcm) {
        this.notificacionRepository = notificacionRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.fcm = fcm;
    }

    /** De dónde se hizo la compra, según el User-Agent. La app se identifica como "FluxGuardApp/1.0". */
    public static String origenDesde(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        if (userAgent.contains("FluxGuardApp")) {
            return ORIGEN_APP;
        }
        boolean android = userAgent.contains("Android");
        if (userAgent.contains("iPad") || userAgent.contains("Tablet") || (android && !userAgent.contains("Mobile"))) {
            return ORIGEN_TABLET;
        }
        return android || userAgent.contains("iPhone") || userAgent.contains("Mobile") ? ORIGEN_CELULAR : ORIGEN_PC;
    }

    /** Se llama una vez al quedar PAGADO el pedido. Si el aviso falla, el pago sigue confirmado. */
    public void avisarCompra(Pedido pedido) {
        if (pedido.getUsuarioId() == null || pedido.getUsuarioId().startsWith(PREFIJO_INVITADO)) {
            return;
        }
        String id = "compra-" + pedido.getFolio();
        try {
            // El webhook de Stripe y el regreso del cliente pueden llegar a la vez
            if (notificacionRepository.existsById(id)) {
                return;
            }
            Notificacion aviso = notificacionRepository.save(new Notificacion(id, pedido.getUsuarioId(), Notificacion.COMPRA,
                    "Alguien compró en tu cuenta", mensajeCompra(pedido), pedido.getFolio(), LocalDateTime.now()));
            enviarPush(aviso);
        } catch (RuntimeException e) {
            log.error("[FluxG] No se pudo avisar de la compra {}: {}", pedido.getFolio(), e.getMessage());
        }
    }

    private static String mensajeCompra(Pedido pedido) {
        String origen = switch (pedido.getDispositivo() == null ? "" : pedido.getDispositivo()) {
            case ORIGEN_APP -> " desde la app";
            case ORIGEN_PC -> " desde una PC";
            case ORIGEN_CELULAR -> " desde un celular";
            case ORIGEN_TABLET -> " desde una tablet";
            default -> "";
        };
        return "Pedido " + pedido.getFolio() + " por $" + pedido.getTotal() + " MXN" + origen + ". Si no fuiste tú, contáctanos.";
    }

    private void enviarPush(Notificacion aviso) {
        if (!fcm.estaConfigurado()) {
            return;
        }
        Map<String, String> datos = Map.of("tipo", aviso.getTipo(), "folio", aviso.getFolio());
        for (DispositivoPush dispositivo : dispositivoRepository.findByUsuarioId(aviso.getUsuarioId())) {
            try {
                if (!fcm.enviar(dispositivo.getToken(), aviso.getTitulo(), aviso.getMensaje(), datos)) {
                    dispositivoRepository.deleteByToken(dispositivo.getToken());
                }
            } catch (FcmException e) {
                // Un celular que falla no debe impedir el aviso a los demás
                log.error("[FluxG] No se pudo enviar el push de {}: {}", aviso.getFolio(), e.getMessage());
            }
        }
    }

    public List<Notificacion> recientes(String usuarioId) {
        return notificacionRepository.findTop50ByUsuarioIdOrderByFechaDesc(usuarioId);
    }

    public long noLeidas(String usuarioId) {
        return notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    public void marcarLeidas(String usuarioId) {
        List<Notificacion> pendientes = notificacionRepository.findByUsuarioIdAndLeidaFalse(usuarioId);
        pendientes.forEach(aviso -> aviso.setLeida(true));
        notificacionRepository.saveAll(pendientes);
    }

    /** Liga el celular a la cuenta. Si el celular ya estaba ligado a otra cuenta, pasa a esta. */
    public void registrarDispositivo(String usuarioId, String token, String plataforma) {
        DispositivoPush dispositivo = dispositivoRepository.findByToken(token)
                .orElseGet(() -> new DispositivoPush(usuarioId, token, plataforma, LocalDateTime.now()));
        dispositivo.setUsuarioId(usuarioId);
        dispositivo.setPlataforma(plataforma);
        dispositivo.setActualizado(LocalDateTime.now());
        dispositivoRepository.save(dispositivo);
    }

    public void quitarDispositivo(String usuarioId, String token) {
        dispositivoRepository.deleteByUsuarioIdAndToken(usuarioId, token);
    }
}
