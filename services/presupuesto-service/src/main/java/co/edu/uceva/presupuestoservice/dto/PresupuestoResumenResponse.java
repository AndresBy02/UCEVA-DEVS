package co.edu.uceva.presupuestoservice.dto;

import co.edu.uceva.presupuestoservice.model.EstadoPresupuesto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PresupuestoResumenResponse(
        UUID id,
        String codigo,
        String nombre,
        String cliente,
        EstadoPresupuesto estado,
        UUID presupuestoBaseId,
        BigDecimal porcentajeAdministracion,
        BigDecimal porcentajeImprevistos,
        BigDecimal porcentajeUtilidad,
        String creadoPor,
        OffsetDateTime fechaCreacion,
        String aprobadoPor,
        OffsetDateTime fechaAprobacion,
        int totalCapitulos,
        int totalItems
) {
}
