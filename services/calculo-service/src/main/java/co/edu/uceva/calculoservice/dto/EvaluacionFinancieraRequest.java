package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record EvaluacionFinancieraRequest(
        BigDecimal tasaDescuento,
        List<BigDecimal> flujosCaja,
        Double toleranciaTir,
        Integer maxIteracionesTir,
        FinanciacionRequest financiacion
) {
    public record FinanciacionRequest(
            BigDecimal capital,
            BigDecimal tasaPeriodo,
            Integer periodos
    ) {}
}
