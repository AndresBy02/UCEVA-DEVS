package co.edu.uceva.insumoservice.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HistorialPrecioResponse(
        UUID id,
        UUID insumoId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioAnterior,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioNuevo,
        String modificadoPor,
        OffsetDateTime fechaCambio
) {
}
