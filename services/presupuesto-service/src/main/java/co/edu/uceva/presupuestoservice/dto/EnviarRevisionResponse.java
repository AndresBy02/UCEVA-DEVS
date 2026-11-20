package co.edu.uceva.presupuestoservice.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.OffsetDateTime;

/**
 * Respuesta del envío a revisión: el detalle recalculado del presupuesto (main)
 * más los datos de la versión congelada y de la notificación (UC-38).
 * Se usa {@link JsonUnwrapped} para no duplicar los campos del detalle.
 */
public record EnviarRevisionResponse(
        @JsonUnwrapped PresupuestoDetalleResponse presupuesto,
        Integer numeroVersion,
        String codigoPresupuesto,
        String nombrePresupuesto,
        String estadoVersion,
        String destinatarioSub,
        OffsetDateTime fechaNotificacion,
        String comentarioEnvio,
        String snapshotJson
) {
}
