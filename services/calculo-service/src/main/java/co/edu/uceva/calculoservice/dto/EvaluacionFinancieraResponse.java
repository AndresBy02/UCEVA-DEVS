package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record EvaluacionFinancieraResponse(
        String motorUtilizado,
        BigDecimal vpn,
        TirResult tir,
        AmortizacionResult amortizacion
) {
    public record TirResult(
            boolean convergio,
            String estado,
            int codigoRetornoC,
            BigDecimal tasaInternaRetorno,
            BigDecimal tasaInternaRetornoPorcentaje,
            int iteraciones,
            String mensaje
    ) {}

    public record AmortizacionResult(
            BigDecimal capital,
            BigDecimal tasaPeriodo,
            int periodos,
            BigDecimal cuotaFija,
            List<FilaAmortizacion> filas
    ) {}

    public record FilaAmortizacion(
            int periodo,
            BigDecimal cuota,
            BigDecimal interes,
            BigDecimal abonoCapital,
            BigDecimal saldo
    ) {}
}
