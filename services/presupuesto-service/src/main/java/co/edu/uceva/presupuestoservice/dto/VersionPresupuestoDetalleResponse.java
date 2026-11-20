package co.edu.uceva.presupuestoservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VersionPresupuestoDetalleResponse(
        UUID id,
        UUID presupuestoId,
        Integer numeroVersion,
        String etiqueta,
        String comentarioEnvio,
        String estadoEnMomento,
        String creadoPor,
        OffsetDateTime fechaCreacion,
        BigDecimal costoDirecto,
        BigDecimal valorAdministracion,
        BigDecimal valorImprevistos,
        BigDecimal valorUtilidad,
        BigDecimal precioVenta,
        PresupuestoDetalleResponse snapshot,
        String codigoPresupuesto,
        String nombrePresupuesto,
        String destinatarioSub,
        OffsetDateTime fechaNotificacion
) {
}
