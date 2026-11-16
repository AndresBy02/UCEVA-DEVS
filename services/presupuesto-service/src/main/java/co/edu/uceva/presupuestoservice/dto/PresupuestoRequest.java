package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record PresupuestoRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 40)
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200)
        String nombre,

        @NotBlank(message = "El cliente es obligatorio")
        @Size(max = 150)
        String cliente,

        @DecimalMin("0")
        @Digits(integer = 3, fraction = 4)
        BigDecimal porcentajeAdministracion,

        @DecimalMin("0")
        @Digits(integer = 3, fraction = 4)
        BigDecimal porcentajeImprevistos,

        @DecimalMin("0")
        @Digits(integer = 3, fraction = 4)
        BigDecimal porcentajeUtilidad,

        @DecimalMin("0")
        @Digits(integer = 17, fraction = 2)
        BigDecimal costoFijo,

        @DecimalMin("0")
        @Digits(integer = 17, fraction = 2)
        BigDecimal precioVentaUnitario,

        @DecimalMin("0")
        @Digits(integer = 17, fraction = 2)
        BigDecimal costoVariableUnitario,

        @Valid
        List<CapituloInicialRequest> capitulos
) {
    public record CapituloInicialRequest(
            @NotBlank @Size(max = 40) String codigo,
            @NotBlank @Size(max = 150) String nombre,
            @Valid List<ItemCreateRequest> items
    ) {}
}
