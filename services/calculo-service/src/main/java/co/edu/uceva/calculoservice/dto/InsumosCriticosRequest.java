package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;

public record InsumosCriticosRequest(
        BigDecimal porcentajeVariacionReferencia,
        CalculoPresupuestoRequest presupuesto
) {
}
