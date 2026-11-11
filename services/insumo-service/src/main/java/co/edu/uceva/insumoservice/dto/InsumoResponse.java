package co.edu.uceva.insumoservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import co.edu.uceva.insumoservice.model.UnidadMedida;

public record InsumoResponse(
        UUID id,
        String codigo,
        String descripcion,
        UnidadMedida unidadMedida,
        BigDecimal precioUnitario,
        boolean activo,
        String updatedBy,
        OffsetDateTime updatedAt,
        List<HistorialPrecioResponse> historialPrecios,
        List<PresupuestoUsoResponse> presupuestos) {
}
