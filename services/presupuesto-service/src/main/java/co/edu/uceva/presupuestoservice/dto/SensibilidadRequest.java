package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SensibilidadRequest(
        @NotBlank String codigoInsumo,
        @NotNull BigDecimal porcentajeVariacion
) {
}
