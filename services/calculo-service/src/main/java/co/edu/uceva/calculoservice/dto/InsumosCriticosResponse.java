package co.edu.uceva.calculoservice.dto;

import java.math.BigDecimal;
import java.util.List;

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
    ) {
    }
}
