package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemCreateRequest(
        @NotNull(message = "El insumoId es obligatorio")
        UUID insumoId,

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
        @Digits(integer = 16, fraction = 3, message = "La cantidad admite como máximo 3 decimales")
        BigDecimal cantidad
) {
}
