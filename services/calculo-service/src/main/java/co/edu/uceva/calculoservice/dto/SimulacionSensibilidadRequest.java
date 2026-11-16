package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;

public record SimulacionSensibilidadRequest(
        String codigoInsumo,
        BigDecimal porcentajeVariacion,
        CalculoPresupuestoRequest presupuesto
) {
}
