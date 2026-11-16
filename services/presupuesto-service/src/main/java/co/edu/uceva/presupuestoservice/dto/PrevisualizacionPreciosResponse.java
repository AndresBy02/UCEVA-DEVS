package co.edu.uceva.presupuestoservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PrevisualizacionPreciosResponse(
        UUID presupuestoId,
        String codigoPresupuesto,
        int totalItemsRevisados,
        int cantidadItemsConCambio,
        List<CambioPrecioItem> cambios
) {
    public record CambioPrecioItem(
            UUID itemId,
            String capituloCodigo,
            UUID insumoId,
            String codigoInsumo,
            String descripcion,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cantidad,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioAnteriorPresupuesto,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioNuevoCatalogo,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal diferenciaUnitaria
    ) {}
}
