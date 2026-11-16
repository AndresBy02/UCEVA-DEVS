package co.edu.uceva.presupuestoservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InsumoCatalogoDto(
        UUID id,
        String codigo,
        String descripcion,
        String unidadMedida,
        BigDecimal precioUnitario,
        boolean activo,
        OffsetDateTime updatedAt
) {
}
