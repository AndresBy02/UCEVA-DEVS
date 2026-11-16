package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;

public record SimulacionSensibilidadResponse(
        String codigoInsumo,
        BigDecimal porcentajeVariacion,
        BigDecimal costoDirectoOriginal,
        BigDecimal costoDirectoSimulado,
        BigDecimal diferenciaCostoDirecto,
        BigDecimal variacionCostoDirectoPorcentaje,
        BigDecimal precioVentaOriginal,
        BigDecimal precioVentaSimulado,
        BigDecimal diferenciaPrecioVenta,
        BigDecimal variacionPrecioVentaPorcentaje,
        CalculoPresupuestoResponse.PuntoEquilibrioResult puntoEquilibrioOriginal,
        CalculoPresupuestoResponse.PuntoEquilibrioResult puntoEquilibrioSimulado,
        CalculoPresupuestoResponse calculoSimulado
) {
}
