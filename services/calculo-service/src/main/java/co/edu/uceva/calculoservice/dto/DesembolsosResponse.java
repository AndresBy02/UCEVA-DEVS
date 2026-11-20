package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record DesembolsosResponse(
        int semanas,
        BigDecimal costoDirectoTotal,
        BigDecimal totalDesembolsado,
        BigDecimal diferenciaValidacion,
        List<CapituloDesembolso> capitulos,
        List<BigDecimal> totalesPorSemana,
        List<BigDecimal> acumuladosPorSemana
) {
    public record CapituloDesembolso(
            String capituloId,
            String codigo,
            String nombre,
            BigDecimal subtotalCapitulo,
            BigDecimal totalCapituloDesembolsado,
            List<BigDecimal> porcentajesPorSemana,
            List<BigDecimal> montosPorSemana
    ) {}
}
