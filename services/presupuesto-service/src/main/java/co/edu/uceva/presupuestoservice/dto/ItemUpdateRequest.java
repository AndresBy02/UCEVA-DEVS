package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemUpdateRequest(
        @NotNull
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
        @Digits(integer = 16, fraction = 3)
        BigDecimal cantidad,

        @DecimalMin("0")
        @Digits(integer = 16, fraction = 3)
        BigDecimal precioUnitarioCopia
) {
}
