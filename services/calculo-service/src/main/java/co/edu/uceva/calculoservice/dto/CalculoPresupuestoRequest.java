package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record CalculoPresupuestoRequest(
        BigDecimal porcentajeAdministracion,
        BigDecimal porcentajeImprevistos,
        BigDecimal porcentajeUtilidad,
        BigDecimal costoFijo,
        BigDecimal precioVentaUnitario,
        BigDecimal costoVariableUnitario,
        List<CapituloRequest> capitulos
) {
    public record CapituloRequest(
            String capituloId,
            String nombre,
            List<ItemRequest> items
    ) {}

    public record ItemRequest(
            String itemId,
            String codigoInsumo,
            String descripcion,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precioUnitario
    ) {}
}
