package co.edu.uceva.calculoservice.service;

import co.edu.uceva.calculoservice.dto.CalculoPresupuestoRequest;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse.CapituloCalculado;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse.ItemCalculado;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse.PuntoEquilibrioResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PresupuestoCalculoService {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CERO_MONEDA = new BigDecimal("0.00");

    public CalculoPresupuestoResponse calcularPresupuesto(CalculoPresupuestoRequest request) {
        List<CapituloCalculado> capitulosOut = new ArrayList<>();
        BigDecimal costoDirecto = CERO_MONEDA;

        List<CalculoPresupuestoRequest.CapituloRequest> capitulosIn =
                request.capitulos() != null ? request.capitulos() : Collections.emptyList();

        for (CalculoPresupuestoRequest.CapituloRequest cap : capitulosIn) {
            List<ItemCalculado> itemsOut = new ArrayList<>();
            BigDecimal subtotalCapitulo = CERO_MONEDA;

            List<CalculoPresupuestoRequest.ItemRequest> itemsIn =
                    cap.items() != null ? cap.items() : Collections.emptyList();

            for (CalculoPresupuestoRequest.ItemRequest item : itemsIn) {
                BigDecimal cantidad = item.cantidad() != null
                        ? item.cantidad().setScale(3, RoundingMode.HALF_UP)
                        : new BigDecimal("0.000");
                BigDecimal precioUnitario = item.precioUnitario() != null
                        ? item.precioUnitario().setScale(3, RoundingMode.HALF_UP)
                        : new BigDecimal("0.000");

                /* Punto único de redondeo del ítem (Protocolo RQ-01) */
                BigDecimal valorParcial = cantidad.multiply(precioUnitario)
                        .setScale(2, RoundingMode.HALF_UP);

                subtotalCapitulo = subtotalCapitulo.add(valorParcial);

                itemsOut.add(new ItemCalculado(
                        item.itemId(),
                        item.codigoInsumo(),
                        cantidad,
                        precioUnitario,
                        valorParcial
                ));
            }

            costoDirecto = costoDirecto.add(subtotalCapitulo);
            capitulosOut.add(new CapituloCalculado(
                    cap.capituloId(),
                    cap.nombre(),
                    subtotalCapitulo,
                    itemsOut
            ));
        }

        BigDecimal porcA = request.porcentajeAdministracion() != null
                ? request.porcentajeAdministracion().setScale(2, RoundingMode.HALF_UP)
                : CERO_MONEDA;
        BigDecimal porcI = request.porcentajeImprevistos() != null
                ? request.porcentajeImprevistos().setScale(2, RoundingMode.HALF_UP)
                : CERO_MONEDA;
        BigDecimal porcU = request.porcentajeUtilidad() != null
                ? request.porcentajeUtilidad().setScale(2, RoundingMode.HALF_UP)
                : CERO_MONEDA;

        /* AIU discriminado (RF-04) */
        BigDecimal valorA = costoDirecto.multiply(porcA)
                .divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal valorI = costoDirecto.multiply(porcI)
                .divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal valorU = costoDirecto.multiply(porcU)
                .divide(CIEN, 2, RoundingMode.HALF_UP);

        /* Precio de venta como suma verificable de los cuatro (RF-04) */
        BigDecimal precioVenta = costoDirecto.add(valorA).add(valorI).add(valorU);

        /* Los dos márgenes sin confundirlos (RF-05) */
        BigDecimal gananciaBruta = precioVenta.subtract(costoDirecto);
        BigDecimal margenSobreCosto = costoDirecto.compareTo(BigDecimal.ZERO) > 0
                ? gananciaBruta.multiply(CIEN).divide(costoDirecto, 2, RoundingMode.HALF_UP)
                : CERO_MONEDA;
        BigDecimal margenSobrePrecioVenta = precioVenta.compareTo(BigDecimal.ZERO) > 0
                ? gananciaBruta.multiply(CIEN).divide(precioVenta, 2, RoundingMode.HALF_UP)
                : CERO_MONEDA;

        PuntoEquilibrioResult puntoEquilibrio = calcularPuntoEquilibrio(
                request.costoFijo(),
                request.precioVentaUnitario(),
                request.costoVariableUnitario()
        );

        return new CalculoPresupuestoResponse(
                capitulosOut,
                costoDirecto,
                porcA,
                valorA,
                porcI,
                valorI,
                porcU,
                valorU,
                precioVenta,
                margenSobreCosto,
                "Margen sobre el costo (Markup = (PV - CD) / CD)",
                margenSobrePrecioVenta,
                "Margen sobre el precio de venta (Gross Margin = (PV - CD) / PV)",
                puntoEquilibrio
        );
    }

    public PuntoEquilibrioResult calcularPuntoEquilibrio(
            BigDecimal costoFijo,
            BigDecimal precioVentaUnitario,
            BigDecimal costoVariableUnitario
    ) {
        if (costoFijo == null || precioVentaUnitario == null || costoVariableUnitario == null) {
            return null;
        }
        BigDecimal cf = costoFijo.setScale(2, RoundingMode.HALF_UP);
        BigDecimal pvu = precioVentaUnitario.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cvu = costoVariableUnitario.setScale(2, RoundingMode.HALF_UP);
        BigDecimal margenContribucion = pvu.subtract(cvu);

        /* Criterio de aceptación RF-06: cuando el margen de contribución es <= 0 */
        if (margenContribucion.compareTo(BigDecimal.ZERO) <= 0) {
            return new PuntoEquilibrioResult(
                    false,
                    null,
                    margenContribucion,
                    "con este precio no hay punto de equilibrio"
            );
        }

        BigDecimal cantidadEquilibrio = cf.divide(margenContribucion, 2, RoundingMode.HALF_UP);
        return new PuntoEquilibrioResult(
                true,
                cantidadEquilibrio,
                margenContribucion,
                "El trabajo deja de perder dinero a partir de " + cantidadEquilibrio + " unidades"
        );
    }
}
