package co.edu.uceva.presupuestoservice.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ComparacionVersionesResponse(
        UUID presupuestoId,
        VersionPresupuestoResumenResponse versionA,
        VersionPresupuestoResumenResponse versionB,
        BigDecimal diferenciaCostoDirecto,
        BigDecimal diferenciaPrecioVenta,
        List<ItemVersionCambio> itemsAgregados,
        List<ItemVersionCambio> itemsEliminados,
        List<ItemVersionModificado> itemsModificados
) {
    public record ItemVersionCambio(
            UUID itemId,
            String capituloCodigo,
            String capituloNombre,
            String codigoInsumo,
            String descripcion,
            String unidadMedida,
            BigDecimal cantidad,
            BigDecimal precioUnitarioCopia,
            BigDecimal valorParcial
    ) {}

    public record ItemVersionModificado(
            UUID itemId,
            String capituloCodigo,
            String capituloNombre,
            String codigoInsumo,
            String descripcion,
            String unidadMedida,
            BigDecimal cantidadAntes,
            BigDecimal cantidadDespues,
            BigDecimal precioAntes,
            BigDecimal precioDespues,
            BigDecimal valorParcialAntes,
            BigDecimal valorParcialDespues,
            BigDecimal diferenciaParcial
    ) {}
}
