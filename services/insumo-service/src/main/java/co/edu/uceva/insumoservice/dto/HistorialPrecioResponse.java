package co.edu.uceva.insumoservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HistorialPrecioResponse(
        UUID id,
        BigDecimal precioAnterior,
        BigDecimal precioNuevo,
        String modificadoPor,
        OffsetDateTime fechaCambio) {
}
