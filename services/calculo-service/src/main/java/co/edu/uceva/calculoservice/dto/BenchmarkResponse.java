package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;

public record BenchmarkResponse(
        int periodos,
        int corridas,
        String motorActivo,
        long tiempoJavaMs,
        Long tiempoNativoMs,
        BigDecimal tirResultado,
        int iteracionesPorCorrida
) {
}
