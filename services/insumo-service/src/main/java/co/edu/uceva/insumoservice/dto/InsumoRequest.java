package co.edu.uceva.insumoservice.dto;

import java.math.BigDecimal;

import co.edu.uceva.insumoservice.model.UnidadMedida;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InsumoRequest(
        @NotBlank @Size(max = 255) String descripcion,
        @NotNull UnidadMedida unidadMedida,
        @NotNull @DecimalMin("0.000") @Digits(integer = 16, fraction = 3) BigDecimal precioUnitario,
        @NotNull Boolean activo) {
}
