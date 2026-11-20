package co.edu.uceva.presupuestoservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Versión congelada de un presupuesto; nunca se modifica después de creada. */
public record VersionPresupuestoResponse(
        UUID id,
        UUID presupuestoId,
        Integer numero,
        String codigoPresupuesto,
        String nombrePresupuesto,
        String estado,
        String creadoPor,
        OffsetDateTime fechaCreacion,
        String destinatarioSub,
        OffsetDateTime fechaNotificacion,
        String mensaje,
        String snapshotJson
) {
}
