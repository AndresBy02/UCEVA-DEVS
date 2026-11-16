package co.edu.uceva.presupuestoservice.dto;

import co.edu.uceva.presupuestoservice.model.EstadoPresupuesto;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PresupuestoDetalleResponse(
        UUID id,
        String codigo,
        String nombre,
        String cliente,
        EstadoPresupuesto estado,
        UUID presupuestoBaseId,
        String creadoPor,
        OffsetDateTime fechaCreacion,
        String aprobadoPor,
        OffsetDateTime fechaAprobacion,
        BigDecimal porcentajeAdministracion,
        BigDecimal porcentajeImprevistos,
        BigDecimal porcentajeUtilidad,
        BigDecimal costoFijo,
        BigDecimal precioVentaUnitario,
        BigDecimal costoVariableUnitario,
        List<CapituloDetalleResponse> capitulos,
        BigDecimal costoDirecto,
        BigDecimal valorAdministracion,
        BigDecimal valorImprevistos,
        BigDecimal valorUtilidad,
        BigDecimal precioVenta,
        BigDecimal margenSobreCostoPorcentaje,
        String rotuloMargenSobreCosto,
        BigDecimal margenSobrePrecioVentaPorcentaje,
        String rotuloMargenSobrePrecioVenta,
        CalculoDtos.PuntoEquilibrioResult puntoEquilibrio
) {
    public record CapituloDetalleResponse(
            UUID id,
            Integer orden,
            String codigo,
            String nombre,
            BigDecimal subtotalCapitulo,
            List<ItemDetalleResponse> items
    ) {}

    public record ItemDetalleResponse(
            UUID id,
            Integer orden,
            UUID insumoId,
            String codigoInsumo,
            String descripcion,
            String unidadMedida,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cantidad,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioUnitarioCopia,
            BigDecimal valorParcial
    ) {}
}
