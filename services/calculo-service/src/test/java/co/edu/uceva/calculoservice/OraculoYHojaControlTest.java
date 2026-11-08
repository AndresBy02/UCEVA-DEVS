package co.edu.uceva.calculoservice;

import co.edu.uceva.calculoservice.dto.CalculoPresupuestoRequest;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoRequest.CapituloRequest;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoRequest.ItemRequest;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse;
import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraRequest;
import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraRequest.FinanciacionRequest;
import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraResponse;
import co.edu.uceva.calculoservice.jna.FinanzasLibrary;
import co.edu.uceva.calculoservice.service.FinanzasEvaluacionService;
import co.edu.uceva.calculoservice.service.PresupuestoCalculoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OraculoYHojaControlTest {

    @Autowired
    private PresupuestoCalculoService presupuestoCalculoService;

    @Autowired
    private FinanzasEvaluacionService finanzasEvaluacionService;

    @Test
    @DisplayName("CR-01: Insumo con precio 12.345 conserva 3 decimales y redondea parcial con HALF_UP")
    void testCr01PrecioTresDecimales() {
        ItemRequest item = new ItemRequest("ITEM-1", "INS-1", "Cemento", "BULTO",
                new BigDecimal("1.000"), new BigDecimal("12.345"));
        CapituloRequest cap = new CapituloRequest("CAP-1", "Preliminares", List.of(item));
        CalculoPresupuestoRequest req = new CalculoPresupuestoRequest(
                new BigDecimal("8.00"), new BigDecimal("3.00"), new BigDecimal("5.00"),
                null, null, null, List.of(cap)
        );

        CalculoPresupuestoResponse res = presupuestoCalculoService.calcularPresupuesto(req);
        assertEquals(new BigDecimal("12.345"), res.capitulos().get(0).items().get(0).precioUnitario());
        assertEquals(new BigDecimal("12.35"), res.capitulos().get(0).items().get(0).valorParcial());
    }

    @Test
    @DisplayName("CR-02: AIU discriminado (A=8%, I=3%, U=5%) y Precio de Venta suma verificable de los cuatro")
    void testCr02AiuDiscriminado() {
        ItemRequest item = new ItemRequest("ITEM-1", "INS-1", "Obra", "UNIDAD",
                new BigDecimal("10.000"), new BigDecimal("100.000"));
        CapituloRequest cap = new CapituloRequest("CAP-1", "Unico", List.of(item));
        CalculoPresupuestoRequest req = new CalculoPresupuestoRequest(
                new BigDecimal("8.00"), new BigDecimal("3.00"), new BigDecimal("5.00"),
                null, null, null, List.of(cap)
        );

        CalculoPresupuestoResponse res = presupuestoCalculoService.calcularPresupuesto(req);
        assertEquals(new BigDecimal("1000.00"), res.costoDirecto());
        assertEquals(new BigDecimal("80.00"), res.valorAdministracion());
        assertEquals(new BigDecimal("30.00"), res.valorImprevistos());
        assertEquals(new BigDecimal("50.00"), res.valorUtilidad());
        assertEquals(new BigDecimal("1160.00"), res.precioVenta());
    }

    @Test
    @DisplayName("CR-03: Los dos margenes sin confundirlos (Costo 100, PV 125 -> 25% sobre costo y 20% sobre venta)")
    void testCr03DosMargenes() {
        ItemRequest item = new ItemRequest("ITEM-1", "INS-1", "Base", "UNIDAD",
                new BigDecimal("1.000"), new BigDecimal("100.000"));
        CapituloRequest cap = new CapituloRequest("CAP-1", "Cap", List.of(item));
        CalculoPresupuestoRequest req = new CalculoPresupuestoRequest(
                new BigDecimal("10.00"), new BigDecimal("5.00"), new BigDecimal("10.00"),
                null, null, null, List.of(cap)
        );

        CalculoPresupuestoResponse res = presupuestoCalculoService.calcularPresupuesto(req);
        assertEquals(new BigDecimal("100.00"), res.costoDirecto());
        assertEquals(new BigDecimal("125.00"), res.precioVenta());
        assertEquals(new BigDecimal("25.00"), res.margenSobreCostoPorcentaje());
        assertEquals(new BigDecimal("20.00"), res.margenSobrePrecioVentaPorcentaje());
    }

    @Test
    @DisplayName("CR-04, CR-05 y CR-06: Punto de equilibrio normal y casos limite con margen <= 0")
    void testCr04a06PuntoEquilibrio() {
        var normal = presupuestoCalculoService.calcularPuntoEquilibrio(
                new BigDecimal("15000000"), new BigDecimal("250000"), new BigDecimal("175000"));
        assertTrue(normal.viable());
        assertEquals(new BigDecimal("200.00"), normal.cantidadEquilibrio());

        var margenCero = presupuestoCalculoService.calcularPuntoEquilibrio(
                new BigDecimal("10000000"), new BigDecimal("100000"), new BigDecimal("100000"));
        assertFalse(margenCero.viable());
        assertNull(margenCero.cantidadEquilibrio());
        assertEquals("con este precio no hay punto de equilibrio", margenCero.mensaje());

        var margenNegativo = presupuestoCalculoService.calcularPuntoEquilibrio(
                new BigDecimal("10000000"), new BigDecimal("90000"), new BigDecimal("100000"));
        assertFalse(margenNegativo.viable());
        assertNull(margenNegativo.cantidadEquilibrio());
        assertEquals("con este precio no hay punto de equilibrio", margenNegativo.mensaje());
    }

    @Test
    @DisplayName("CR-07: Caso Limite - Presupuesto vacio devuelve $0.00 en todo sin excepcion")
    void testCr07PresupuestoVacio() {
        CalculoPresupuestoRequest req = new CalculoPresupuestoRequest(
                new BigDecimal("8.00"), new BigDecimal("3.00"), new BigDecimal("5.00"),
                null, null, null, List.of()
        );
        CalculoPresupuestoResponse res = presupuestoCalculoService.calcularPresupuesto(req);
        assertEquals(new BigDecimal("0.00"), res.costoDirecto());
        assertEquals(new BigDecimal("0.00"), res.precioVenta());
        assertEquals(new BigDecimal("0.00"), res.margenSobreCostoPorcentaje());
    }

    @Test
    @DisplayName("CR-08 a CR-14: VPN, TIR convergente, flujos sin cambio de signo, no convergencia y amortizacion")
    void testCr08a14EvaluacionFinanciera() {
        /* CR-08 y CR-11 + CR-14 */
        EvaluacionFinancieraRequest req = new EvaluacionFinancieraRequest(
                new BigDecimal("0.015"),
                List.of(
                        new BigDecimal("-10000.00"),
                        new BigDecimal("3500.00"),
                        new BigDecimal("4000.00"),
                        new BigDecimal("4500.00")
                ),
                1e-9,
                1000,
                new FinanciacionRequest(new BigDecimal("10000.00"), new BigDecimal("0.02"), 3)
        );
        EvaluacionFinancieraResponse res = finanzasEvaluacionService.evaluarProyecto(req);
        assertTrue(res.tir().convergio());
        assertEquals(new BigDecimal("0.092825"), res.tir().tasaInternaRetorno());
        assertEquals(new BigDecimal("3467.55"), res.amortizacion().cuotaFija());
        assertEquals(new BigDecimal("0.00"), res.amortizacion().filas().get(2).saldo());

        /* CR-12: Flujo sin cambio de signo */
        EvaluacionFinancieraRequest reqSinSigno = new EvaluacionFinancieraRequest(
                new BigDecimal("0.01"),
                List.of(new BigDecimal("-1000"), new BigDecimal("-500"), new BigDecimal("-200")),
                1e-9,
                1000,
                null
        );
        EvaluacionFinancieraResponse resSinSigno = finanzasEvaluacionService.evaluarProyecto(reqSinSigno);
        assertFalse(resSinSigno.tir().convergio());
        assertEquals("no convergió", resSinSigno.tir().estado());
        assertEquals(FinanzasLibrary.FIN_ERR_SIN_CAMBIO_SIGNO, resSinSigno.tir().codigoRetornoC());
        assertNull(resSinSigno.tir().tasaInternaRetorno());

        /* CR-13: Flujo que no converge reporta 'no convergió' y el numero de iteraciones (nunca devuelve 0) */
        EvaluacionFinancieraRequest reqNoConv = new EvaluacionFinancieraRequest(
                new BigDecimal("0.01"),
                List.of(new BigDecimal("-1000"), new BigDecimal("500"), new BigDecimal("-500")),
                1e-12,
                50,
                null
        );
        EvaluacionFinancieraResponse resNoConv = finanzasEvaluacionService.evaluarProyecto(reqNoConv);
        assertFalse(resNoConv.tir().convergio());
        assertEquals("no convergió", resNoConv.tir().estado());
        assertEquals(FinanzasLibrary.FIN_ERR_NO_CONVERGIO, resNoConv.tir().codigoRetornoC());
        assertEquals(50, resNoConv.tir().iteraciones());
        assertNull(resNoConv.tir().tasaInternaRetorno());
    }

    @Test
    @DisplayName("CR-15 (RQ-01): Conciliacion de 200 items contra hoja_control_200_items.csv con desviacion de $0.00")
    void testRq01ConciliacionHojaControl200Items() throws Exception {
        Path csvPath = resolverRutaHojaControl();
        assertTrue(Files.exists(csvPath), "Debe existir la hoja de control en " + csvPath.toAbsolutePath());

        Map<String, List<ItemRequest>> itemsPorCap = new LinkedHashMap<>();
        Map<String, String> nombresCap = new LinkedHashMap<>();
        Map<String, BigDecimal> valorParcialEsperadoPorItem = new LinkedHashMap<>();

        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String header = br.readLine(); // saltar cabecera
            assertNotNull(header);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = line.split(",");
                String itemId = cols[1];
                String capId = cols[2];
                String capNombre = cols[3];
                String codInsumo = cols[4];
                String desc = cols[5];
                String unidad = cols[6];
                BigDecimal cantidad = new BigDecimal(cols[7]);
                BigDecimal precioUnitario = new BigDecimal(cols[8]);
                BigDecimal parcialEsperado = new BigDecimal(cols[10]);

                nombresCap.putIfAbsent(capId, capNombre);
                itemsPorCap.computeIfAbsent(capId, k -> new ArrayList<>())
                        .add(new ItemRequest(itemId, codInsumo, desc, unidad, cantidad, precioUnitario));
                valorParcialEsperadoPorItem.put(itemId, parcialEsperado);
            }
        }

        assertEquals(200, valorParcialEsperadoPorItem.size(), "La hoja de control debe tener exactamente 200 items");

        List<CapituloRequest> capitulosReq = new ArrayList<>();
        for (Map.Entry<String, List<ItemRequest>> entry : itemsPorCap.entrySet()) {
            capitulosReq.add(new CapituloRequest(entry.getKey(), nombresCap.get(entry.getKey()), entry.getValue()));
        }

        CalculoPresupuestoRequest request = new CalculoPresupuestoRequest(
                new BigDecimal("8.00"),
                new BigDecimal("3.00"),
                new BigDecimal("5.00"),
                null, null, null,
                capitulosReq
        );

        CalculoPresupuestoResponse response = presupuestoCalculoService.calcularPresupuesto(request);

        /* 1. Verificar 200 de 200 valores parciales con desviación $0.00 */
        int itemsVerificados = 0;
        for (var cap : response.capitulos()) {
            for (var item : cap.items()) {
                BigDecimal esperado = valorParcialEsperadoPorItem.get(item.itemId());
                assertEquals(esperado, item.valorParcial(),
                        "Desviacion detectada en el item " + item.itemId());
                itemsVerificados++;
            }
        }
        assertEquals(200, itemsVerificados);

        /* 2. Verificar los 5 subtotales de capítulo contra la hoja de control */
        Map<String, BigDecimal> subtotalesEsperados = Map.of(
                "CAP-01", new BigDecimal("891086.71"),
                "CAP-02", new BigDecimal("1055433.36"),
                "CAP-03", new BigDecimal("1236102.03"),
                "CAP-04", new BigDecimal("879509.60"),
                "CAP-05", new BigDecimal("1149302.01")
        );
        for (var cap : response.capitulos()) {
            assertEquals(subtotalesEsperados.get(cap.capituloId()), cap.subtotalCapitulo(),
                    "Desviacion en subtotal del capitulo " + cap.capituloId());
        }

        /* 3. Verificar Costo Directo, AIU discriminado y Precio de Venta con desviación $0.00 */
        assertEquals(new BigDecimal("5211433.71"), response.costoDirecto());
        assertEquals(new BigDecimal("416914.70"), response.valorAdministracion());
        assertEquals(new BigDecimal("156343.01"), response.valorImprevistos());
        assertEquals(new BigDecimal("260571.69"), response.valorUtilidad());
        assertEquals(new BigDecimal("6045263.11"), response.precioVenta());
        assertEquals(new BigDecimal("16.00"), response.margenSobreCostoPorcentaje());
        assertEquals(new BigDecimal("13.79"), response.margenSobrePrecioVentaPorcentaje());
    }

    private Path resolverRutaHojaControl() {
        Path p1 = Paths.get("docs/control/hoja_control_200_items.csv");
        if (Files.exists(p1)) return p1;
        Path p2 = Paths.get("../../docs/control/hoja_control_200_items.csv");
        if (Files.exists(p2)) return p2;
        return p1;
    }
}
