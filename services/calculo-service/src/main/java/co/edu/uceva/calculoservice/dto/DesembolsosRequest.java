package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record DesembolsosRequest(
        Integer semanas,
        List<CapituloDesembolsoInput> capitulos
) {
    public record CapituloDesembolsoInput(
            String capituloId,
            String codigo,
            String nombre,
            BigDecimal subtotalCapitulo,
            List<BigDecimal> porcentajesPorSemana
    ) {}
}
