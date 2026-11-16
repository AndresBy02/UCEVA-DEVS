package co.edu.uceva.presupuestoservice.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ComparacionEscenariosResponse(
        EscenarioResumen escenarioA,
        EscenarioResumen escenarioB,
        List<ComparacionCapitulo> capitulos,
        ComparacionTotal comparacionTotal
) {
    public record EscenarioResumen(
            UUID id,
            String codigo,
            String nombre,
            BigDecimal costoDirecto,
            BigDecimal valorAdministracion,
            BigDecimal valorImprevistos,
            BigDecimal valorUtilidad,
            BigDecimal precioVenta
    ) {}

    public record ComparacionCapitulo(
            String codigoCapitulo,
            String nombreCapitulo,
            BigDecimal subtotalEscenarioA,
            BigDecimal subtotalEscenarioB,
            BigDecimal diferenciaAbsoluta,
            BigDecimal diferenciaPorcentual
    ) {}

    public record ComparacionTotal(
            BigDecimal diferenciaCostoDirectoAbsoluta,
            BigDecimal diferenciaCostoDirectoPorcentual,
            BigDecimal diferenciaPrecioVentaAbsoluta,
            BigDecimal diferenciaPrecioVentaPorcentual
    ) {}
}
