package co.edu.uceva.presupuestoservice.dto;

import java.math.BigDecimal;
import java.util.List;

public final class CalculoDtos {

    private CalculoDtos() {
    }

    public record CalculoPresupuestoRequest(
            BigDecimal porcentajeAdministracion,
            BigDecimal porcentajeImprevistos,
            BigDecimal porcentajeUtilidad,
            BigDecimal costoFijo,
            BigDecimal precioVentaUnitario,
            BigDecimal costoVariableUnitario,
            List<CapituloRequest> capitulos
    ) {
        public record CapituloRequest(String capituloId, String nombre, List<ItemRequest> items) {}

        public record ItemRequest(
                String itemId,
                String codigoInsumo,
                String descripcion,
                String unidad,
                BigDecimal cantidad,
                BigDecimal precioUnitario
        ) {}
    }

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
    }

    public record PuntoEquilibrioResult(
            boolean viable,
            BigDecimal cantidadEquilibrio,
            BigDecimal margenContribucionUnitario,
            String mensaje
    ) {}

    public record SimulacionSensibilidadRequest(
            String codigoInsumo,
            BigDecimal porcentajeVariacion,
            CalculoPresupuestoRequest presupuesto
    ) {}

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
            PuntoEquilibrioResult puntoEquilibrioOriginal,
            PuntoEquilibrioResult puntoEquilibrioSimulado,
            CalculoPresupuestoResponse calculoSimulado
    ) {}

    public record InsumosCriticosRequest(
            BigDecimal porcentajeVariacionReferencia,
            CalculoPresupuestoRequest presupuesto
    ) {}

    public record InsumosCriticosResponse(
            BigDecimal costoDirectoBase,
            BigDecimal precioVentaBase,
            BigDecimal porcentajeVariacionReferencia,
            List<InsumoCriticoItem> insumosCriticos
    ) {
        public record InsumoCriticoItem(
                String codigoInsumo,
                String descripcion,
                String unidad,
                BigDecimal cantidadTotal,
                BigDecimal aporteCostoDirecto,
                BigDecimal participacionPorcentaje,
                BigDecimal impactoCostoDirecto,
                BigDecimal impactoPrecioVenta
        ) {}
    }

    public record DesembolsosRequest(
            Integer semanas,
            List<CapituloDesembolsoInput> capitulos
    ) {
        public record CapituloDesembolsoInput(
                String capituloId,
                String codigo,
                String nombre,
                BigDecimal subtotalCapitulo,
                List<BigDecimal> porcentajesPorSemana
        ) {}
    }

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
}
