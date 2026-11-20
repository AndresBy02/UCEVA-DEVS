package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;

public record BenchmarkResponse(
        int periodos,
        int corridas,
        String motorActivo,
        long tiempoJavaMs,
        Long tiempoNativoMs,
        Long tiempoNativoPorIteracionMs,
        double tiempoJavaMsExacto,
        Double tiempoNativoMsExacto,
        Double tiempoNativoPorIteracionMsExacto,
        long totalLlamadasJnaPorCorrida,
        long totalLlamadasJnaPorIteracion,
        Double factorSobrecostoJna,
        BigDecimal tirResultado,
        int iteracionesPorCorrida
) {
}
