package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record CalculoPresupuestoResponse(
        List<CapituloCalculado> capitulos,
        BigDecimal costoDirecto,
        BigDecimal porcentajeAdministracion,
        BigDecimal valorAdministracion,
        BigDecimal porcentajeImprevistos,
        BigDecimal valorImprevistos,
        BigDecimal porcentajeUtilidad,
        BigDecimal valorUtilidad,
        BigDecimal precioVenta,
        BigDecimal margenSobreCostoPorcentaje,
        String rotuloMargenSobreCosto,
        BigDecimal margenSobrePrecioVentaPorcentaje,
        String rotuloMargenSobrePrecioVenta,
        PuntoEquilibrioResult puntoEquilibrio
) {
    public record CapituloCalculado(
            String capituloId,
            String nombre,
            BigDecimal subtotalCapitulo,
            List<ItemCalculado> items
    ) {}

    public record ItemCalculado(
            String itemId,
            String codigoInsumo,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal valorParcial
    ) {}

    public record PuntoEquilibrioResult(
            boolean viable,
            BigDecimal cantidadEquilibrio,
            BigDecimal margenContribucionUnitario,
            String mensaje
    ) {}
}
