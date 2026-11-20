package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DesembolsoConfigRequest(
        @NotNull @Min(1) @Max(260) Integer semanas,
        @NotEmpty @Valid List<CapituloDesembolsoConfig> capitulos
) {
    public record CapituloDesembolsoConfig(
            @NotNull UUID capituloId,
            @NotEmpty List<BigDecimal> porcentajesPorSemana
    ) {}
}
