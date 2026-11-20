package co.edu.uceva.calculoservice.service;

import co.edu.uceva.calculoservice.dto.CalculoPresupuestoRequest;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse.CapituloCalculado;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse.ItemCalculado;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse.PuntoEquilibrioResult;
import co.edu.uceva.calculoservice.dto.DesembolsosRequest;
import co.edu.uceva.calculoservice.dto.DesembolsosResponse;
import co.edu.uceva.calculoservice.dto.DesembolsosResponse.CapituloDesembolso;
import co.edu.uceva.calculoservice.dto.InsumosCriticosRequest;
import co.edu.uceva.calculoservice.dto.InsumosCriticosResponse;
import co.edu.uceva.calculoservice.dto.InsumosCriticosResponse.InsumoCriticoItem;
import co.edu.uceva.calculoservice.dto.SimulacionSensibilidadRequest;
import co.edu.uceva.calculoservice.dto.SimulacionSensibilidadResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public SimulacionSensibilidadResponse simularSensibilidad(SimulacionSensibilidadRequest request) {
        CalculoPresupuestoRequest baseReq = request.presupuesto();
        CalculoPresupuestoResponse base = calcularPresupuesto(baseReq);

        BigDecimal porcVar = request.porcentajeVariacion() != null
                ? request.porcentajeVariacion().setScale(2, RoundingMode.HALF_UP)
                : CERO_MONEDA;
        BigDecimal factorPorcentual = CIEN.add(porcVar);
        if (factorPorcentual.compareTo(BigDecimal.ZERO) < 0) {
            factorPorcentual = BigDecimal.ZERO;
        }

        List<CalculoPresupuestoRequest.CapituloRequest> capitulosMod =
                reemplazarPrecioInsumo(baseReq.capitulos(), request.codigoInsumo(), factorPorcentual);

        CalculoPresupuestoRequest preSimReq = new CalculoPresupuestoRequest(
                baseReq.porcentajeAdministracion(),
                baseReq.porcentajeImprevistos(),
                baseReq.porcentajeUtilidad(),
                baseReq.costoFijo(),
                baseReq.precioVentaUnitario(),
                baseReq.costoVariableUnitario(),
                capitulosMod
        );
        CalculoPresupuestoResponse calculoDirectoMod = calcularPresupuesto(preSimReq);

        // Escala proporcionalmente el costo variable unitario segun la variacion del costo directo
        BigDecimal cvuSimulado = baseReq.costoVariableUnitario();
        if (cvuSimulado != null && base.costoDirecto().compareTo(BigDecimal.ZERO) > 0) {
            cvuSimulado = cvuSimulado.multiply(calculoDirectoMod.costoDirecto())
                    .divide(base.costoDirecto(), 2, RoundingMode.HALF_UP);
        }

        CalculoPresupuestoRequest simReq = new CalculoPresupuestoRequest(
                baseReq.porcentajeAdministracion(),
                baseReq.porcentajeImprevistos(),
                baseReq.porcentajeUtilidad(),
                baseReq.costoFijo(),
                baseReq.precioVentaUnitario(),
                cvuSimulado,
                capitulosMod
        );
        CalculoPresupuestoResponse simulado = calcularPresupuesto(simReq);

        BigDecimal difCd = simulado.costoDirecto().subtract(base.costoDirecto());
        BigDecimal varCdPct = base.costoDirecto().compareTo(BigDecimal.ZERO) > 0
                ? difCd.multiply(CIEN).divide(base.costoDirecto(), 2, RoundingMode.HALF_UP)
                : CERO_MONEDA;

        BigDecimal difPv = simulado.precioVenta().subtract(base.precioVenta());
        BigDecimal varPvPct = base.precioVenta().compareTo(BigDecimal.ZERO) > 0
                ? difPv.multiply(CIEN).divide(base.precioVenta(), 2, RoundingMode.HALF_UP)
                : CERO_MONEDA;

        return new SimulacionSensibilidadResponse(
                request.codigoInsumo(),
                porcVar,
                base.costoDirecto(),
                simulado.costoDirecto(),
                difCd,
                varCdPct,
                base.precioVenta(),
                simulado.precioVenta(),
                difPv,
                varPvPct,
                base.puntoEquilibrio(),
                simulado.puntoEquilibrio(),
                simulado
        );
    }

    public InsumosCriticosResponse identificarInsumosCriticos(InsumosCriticosRequest request) {
        CalculoPresupuestoRequest baseReq = request.presupuesto();
        CalculoPresupuestoResponse base = calcularPresupuesto(baseReq);
        BigDecimal porcRef = request.porcentajeVariacionReferencia() != null
                ? request.porcentajeVariacionReferencia().setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("10.00");

        Map<String, InsumoAcumulado> acumulados = new LinkedHashMap<>();
        List<CalculoPresupuestoRequest.CapituloRequest> capitulosIn =
                baseReq.capitulos() != null ? baseReq.capitulos() : Collections.emptyList();

        for (var cap : capitulosIn) {
            if (cap.items() == null) continue;
            for (var item : cap.items()) {
                if (item.codigoInsumo() == null) continue;
                BigDecimal cantidad = item.cantidad() != null
                        ? item.cantidad().setScale(3, RoundingMode.HALF_UP)
                        : new BigDecimal("0.000");
                BigDecimal precio = item.precioUnitario() != null
                        ? item.precioUnitario().setScale(3, RoundingMode.HALF_UP)
                        : new BigDecimal("0.000");
                BigDecimal parcial = cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP);

                acumulados.compute(item.codigoInsumo(), (k, existing) -> {
                    if (existing == null) {
                        return new InsumoAcumulado(item.codigoInsumo(), item.descripcion(),
                                item.unidad(), cantidad, parcial);
                    }
                    return new InsumoAcumulado(
                            existing.codigo(),
                            existing.descripcion() != null ? existing.descripcion() : item.descripcion(),
                            existing.unidad() != null ? existing.unidad() : item.unidad(),
                            existing.cantidadTotal().add(cantidad),
                            existing.aporte().add(parcial)
                    );
                });
            }
        }

        List<InsumoCriticoItem> criticos = new ArrayList<>();
        for (InsumoAcumulado acc : acumulados.values()) {
            SimulacionSensibilidadResponse sim = simularSensibilidad(
                    new SimulacionSensibilidadRequest(acc.codigo(), porcRef, baseReq));
            BigDecimal participacion = base.costoDirecto().compareTo(BigDecimal.ZERO) > 0
                    ? acc.aporte().multiply(CIEN).divide(base.costoDirecto(), 2, RoundingMode.HALF_UP)
                    : CERO_MONEDA;

            criticos.add(new InsumoCriticoItem(
                    acc.codigo(),
                    acc.descripcion(),
                    acc.unidad(),
                    acc.cantidadTotal(),
                    acc.aporte(),
                    participacion,
                    sim.diferenciaCostoDirecto(),
                    sim.diferenciaPrecioVenta()
            ));
        }

        criticos.sort(Comparator.comparing(InsumoCriticoItem::impactoPrecioVenta)
                .thenComparing(InsumoCriticoItem::aporteCostoDirecto)
                .reversed());

        return new InsumosCriticosResponse(
                base.costoDirecto(),
                base.precioVenta(),
                porcRef,
                criticos
        );
    }

    /**
     * Calcula el cronograma semanal de desembolsos por capítulo (UC-42 / RF-12),
     * garantizando mediante ajuste exacto de residuo en la última semana activa
     * que la suma de todos los desembolsos coincida hasta el centavo con el
     * Costo Directo total.
     */
    public DesembolsosResponse calcularDesembolsos(DesembolsosRequest request) {
        if (request == null || request.semanas() == null || request.semanas() < 1 || request.semanas() > 260) {
            throw new IllegalArgumentException("El número de semanas debe estar entre 1 y 260.");
        }
        int semanas = request.semanas();
        List<DesembolsosRequest.CapituloDesembolsoInput> capsIn =
                request.capitulos() != null ? request.capitulos() : Collections.emptyList();

        List<CapituloDesembolso> capsOut = new ArrayList<>(capsIn.size());
        BigDecimal[] totalesSemana = new BigDecimal[semanas];
        for (int s = 0; s < semanas; s++) {
            totalesSemana[s] = CERO_MONEDA;
        }
        BigDecimal costoDirectoTotal = CERO_MONEDA;
        BigDecimal totalDesembolsado = CERO_MONEDA;

        for (var cap : capsIn) {
            BigDecimal subtotal = cap.subtotalCapitulo() != null
                    ? cap.subtotalCapitulo().setScale(2, RoundingMode.HALF_UP)
                    : CERO_MONEDA;
            costoDirectoTotal = costoDirectoTotal.add(subtotal);

            List<BigDecimal> porcentajesRaw = cap.porcentajesPorSemana() != null
                    ? cap.porcentajesPorSemana()
                    : Collections.emptyList();
            if (porcentajesRaw.size() != semanas) {
                throw new IllegalArgumentException(
                        "El capítulo " + cap.codigo() + " debe especificar exactamente " + semanas + " porcentajes.");
            }

            List<BigDecimal> porcentajes = new ArrayList<>(semanas);
            BigDecimal sumaPct = BigDecimal.ZERO;
            int ultimaSemanaActiva = -1;
            for (int s = 0; s < semanas; s++) {
                BigDecimal pct = porcentajesRaw.get(s) != null
                        ? porcentajesRaw.get(s).setScale(4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                if (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(CIEN) > 0) {
                    throw new IllegalArgumentException(
                            "Los porcentajes de avance deben estar entre 0 y 100 en el capítulo " + cap.codigo());
                }
                if (pct.compareTo(BigDecimal.ZERO) > 0) {
                    ultimaSemanaActiva = s;
                }
                porcentajes.add(pct);
                sumaPct = sumaPct.add(pct);
            }

            if (subtotal.compareTo(BigDecimal.ZERO) > 0
                    && sumaPct.subtract(CIEN).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new IllegalArgumentException(
                        "La suma de avance del capítulo " + cap.codigo() + " debe ser 100% (actual: "
                                + sumaPct.setScale(2, RoundingMode.HALF_UP) + "%).");
            }

            List<BigDecimal> montos = new ArrayList<>(Collections.nCopies(semanas, CERO_MONEDA));
            BigDecimal acumuladoCap = CERO_MONEDA;
            if (subtotal.compareTo(BigDecimal.ZERO) > 0 && ultimaSemanaActiva >= 0) {
                for (int s = 0; s < semanas; s++) {
                    if (s == ultimaSemanaActiva) {
                        BigDecimal residuo = subtotal.subtract(acumuladoCap).setScale(2, RoundingMode.HALF_UP);
                        montos.set(s, residuo);
                        acumuladoCap = acumuladoCap.add(residuo);
                    } else {
                        BigDecimal montoSemana = subtotal.multiply(porcentajes.get(s))
                                .divide(CIEN, 2, RoundingMode.HALF_UP);
                        montos.set(s, montoSemana);
                        acumuladoCap = acumuladoCap.add(montoSemana);
                    }
                }
            }

            for (int s = 0; s < semanas; s++) {
                totalesSemana[s] = totalesSemana[s].add(montos.get(s));
            }
            totalDesembolsado = totalDesembolsado.add(acumuladoCap);

            capsOut.add(new CapituloDesembolso(
                    cap.capituloId(),
                    cap.codigo(),
                    cap.nombre(),
                    subtotal,
                    acumuladoCap,
                    porcentajes,
                    montos
            ));
        }

        List<BigDecimal> totalesOut = new ArrayList<>(semanas);
        List<BigDecimal> acumuladosOut = new ArrayList<>(semanas);
        BigDecimal corr = CERO_MONEDA;
        for (int s = 0; s < semanas; s++) {
            totalesOut.add(totalesSemana[s]);
            corr = corr.add(totalesSemana[s]);
            acumuladosOut.add(corr);
        }

        return new DesembolsosResponse(
                semanas,
                costoDirectoTotal,
                totalDesembolsado,
                costoDirectoTotal.subtract(totalDesembolsado).setScale(2, RoundingMode.HALF_UP),
                capsOut,
                totalesOut,
                acumuladosOut
        );
    }

    private List<CalculoPresupuestoRequest.CapituloRequest> reemplazarPrecioInsumo(
            List<CalculoPresupuestoRequest.CapituloRequest> capitulos,
            String codigoObjetivo,
            BigDecimal factorPorcentual) {
        if (capitulos == null) {
            return Collections.emptyList();
        }
        List<CalculoPresupuestoRequest.CapituloRequest> resultado = new ArrayList<>(capitulos.size());
        for (var cap : capitulos) {
            List<CalculoPresupuestoRequest.ItemRequest> itemsMod = new ArrayList<>();
            if (cap.items() != null) {
                for (var item : cap.items()) {
                    BigDecimal nuevoPrecio = item.precioUnitario();
                    if (codigoObjetivo != null && codigoObjetivo.equals(item.codigoInsumo()) && nuevoPrecio != null) {
                        nuevoPrecio = nuevoPrecio.multiply(factorPorcentual)
                                .divide(CIEN, 3, RoundingMode.HALF_UP);
                    }
                    itemsMod.add(new CalculoPresupuestoRequest.ItemRequest(
                            item.itemId(),
                            item.codigoInsumo(),
                            item.descripcion(),
                            item.unidad(),
                            item.cantidad(),
                            nuevoPrecio
                    ));
                }
            }
            resultado.add(new CalculoPresupuestoRequest.CapituloRequest(cap.capituloId(), cap.nombre(), itemsMod));
        }
        return resultado;
    }

    private record InsumoAcumulado(
            String codigo,
            String descripcion,
            String unidad,
            BigDecimal cantidadTotal,
            BigDecimal aporte
    ) {}
}
