package co.edu.uceva.insumoservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import co.edu.uceva.insumoservice.model.UnidadMedida;

public record InsumoResumenResponse(
        UUID id,
        String codigo,
        String descripcion,
        UnidadMedida unidadMedida,
        BigDecimal precioUnitario,
        boolean activo,
        String updatedBy,
        OffsetDateTime updatedAt) {
}
