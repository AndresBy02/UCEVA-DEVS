package co.edu.uceva.presupuestoservice;

import co.edu.uceva.presupuestoservice.client.CalculoMotorClient;
import co.edu.uceva.presupuestoservice.client.InsumoCatalogoClient;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoResponse.CapituloCalculado;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoResponse.ItemCalculado;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.PuntoEquilibrioResult;
import co.edu.uceva.presupuestoservice.dto.InsumoCatalogoDto;
import co.edu.uceva.presupuestoservice.dto.PresupuestoDetalleResponse;
import co.edu.uceva.presupuestoservice.dto.PresupuestoRequest;
import co.edu.uceva.presupuestoservice.dto.PresupuestoRequest.CapituloInicialRequest;
import co.edu.uceva.presupuestoservice.dto.ItemCreateRequest;
import co.edu.uceva.presupuestoservice.service.PresupuestoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:presupuesto_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class PresupuestoFlujoCompletoYHojaControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PresupuestoService presupuestoService;

    @MockitoBean
    private InsumoCatalogoClient insumoCatalogoClient;

    @MockitoBean
    private CalculoMotorClient calculoMotorClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final Map<UUID, InsumoCatalogoDto> catalogoEnMemoria = new HashMap<>();

    @BeforeEach
    void configurarMocks() {
        catalogoEnMemoria.clear();

        given(jwtDecoder.decode("token-presupuestador")).willReturn(construirJwt("sub-ana-01", "presupuestador"));
        given(jwtDecoder.decode("token-revisor")).willReturn(construirJwt("sub-carlos-02", "revisor"));
        given(jwtDecoder.decode("token-firma-alterada")).willThrow(new BadJwtException("Firma alterada"));

        given(insumoCatalogoClient.obtenerInsumo(any(UUID.class))).willAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return catalogoEnMemoria.get(id);
        });

        given(calculoMotorClient.calcularPresupuesto(any(CalculoPresupuestoRequest.class)))
                .willAnswer(inv -> ejecutarProtocoloCalculoExacto(inv.getArgument(0)));
    }

    @Test
    @DisplayName("UC-52, UC-53, UC-54, UC-25, UC-27: Snapshot de precio, preview/aplicacion explicita, roles e identidad sub")
    void flujoSeguridadSnapshotPreciosYAprobacion() throws Exception {
        UUID insumoId = UUID.randomUUID();
        catalogoEnMemoria.put(insumoId, new InsumoCatalogoDto(
                insumoId, "CEM-001", "Cemento gris", "BULTO",
                new BigDecimal("12.345"), true, OffsetDateTime.now(ZoneOffset.UTC)));

        mockMvc.perform(get("/api/v1/presupuestos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/presupuestos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-firma-alterada"))
                .andExpect(status().isUnauthorized());

        String crearBody = """
                {"codigo":"PRE-001","nombre":"Edificio UCEVA","cliente":"Municipio Tulua"}
                """;
        mockMvc.perform(post("/api/v1/presupuestos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crearBody))
                .andExpect(status().isForbidden());

        String respCrear = mockMvc.perform(post("/api/v1/presupuestos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crearBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creadoPor").value("sub-ana-01"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        UUID presupuestoId = UUID.fromString(extraerCampoJson(respCrear, "id"));

        String respCap = mockMvc.perform(post("/api/v1/presupuestos/{id}/capitulos", presupuestoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"CAP-01","nombre":"Preliminares"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        UUID capituloId = UUID.fromString(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(respCap)
                        .get("capitulos").get(0).get("id").asText());

        mockMvc.perform(post("/api/v1/presupuestos/{id}/capitulos/{capId}/items", presupuestoId, capituloId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"insumoId\":\"" + insumoId + "\",\"cantidad\":\"2.000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitulos[0].items[0].precioUnitarioCopia").value("12.345"));

        // Sube el catalogo a 15.000: el presupuesto guardado debe conservar 12.345 (UC-27)
        catalogoEnMemoria.put(insumoId, new InsumoCatalogoDto(
                insumoId, "CEM-001", "Cemento gris", "BULTO",
                new BigDecimal("15.000"), true, OffsetDateTime.now(ZoneOffset.UTC)));

        mockMvc.perform(get("/api/v1/presupuestos/{id}", presupuestoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitulos[0].items[0].precioUnitarioCopia").value("12.345"));

        // Preview muestra cambio sin alterar BD
        mockMvc.perform(get("/api/v1/presupuestos/{id}/actualizar-precios/preview", presupuestoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadItemsConCambio").value(1))
                .andExpect(jsonPath("$.cambios[0].precioAnteriorPresupuesto").value("12.345"))
                .andExpect(jsonPath("$.cambios[0].precioNuevoCatalogo").value("15.000"));

        mockMvc.perform(get("/api/v1/presupuestos/{id}", presupuestoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador"))
                .andExpect(jsonPath("$.capitulos[0].items[0].precioUnitarioCopia").value("12.345"));

        // Confirmacion explicita actualiza a 15.000
        mockMvc.perform(post("/api/v1/presupuestos/{id}/actualizar-precios", presupuestoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitulos[0].items[0].precioUnitarioCopia").value("15.000"));

        // Presupuestador no puede aprobar su propio presupuesto (UC-53)
        mockMvc.perform(post("/api/v1/presupuestos/{id}/aprobar", presupuestoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador"))
                .andExpect(status().isForbidden());

        // Revisor si puede aprobar y queda registrado su sub distinto
        mockMvc.perform(post("/api/v1/presupuestos/{id}/aprobar", presupuestoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.creadoPor").value("sub-ana-01"))
                .andExpect(jsonPath("$.aprobadoPor").value("sub-carlos-02"));

        // Presupuesto aprobado bloquea actualizaciones de precios y edicion
        mockMvc.perform(post("/api/v1/presupuestos/{id}/actualizar-precios", presupuestoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/presupuestos/{id}", presupuestoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Editado","cliente":"Cliente","porcentajeAdministracion":8,"porcentajeImprevistos":3,"porcentajeUtilidad":5}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UC-20, UC-32 y UC-33: Duplicar presupuesto como escenario B, comparar con A y consultar presupuestos por insumo")
    void duplicarYCompararEscenarios() throws Exception {
        UUID insumoId = UUID.randomUUID();
        catalogoEnMemoria.put(insumoId, new InsumoCatalogoDto(
                insumoId, "ACERO-1", "Acero estructural", "KG",
                new BigDecimal("100.000"), true, OffsetDateTime.now(ZoneOffset.UTC)));
        UUID otroInsumoId = UUID.randomUUID();
        catalogoEnMemoria.put(otroInsumoId, new InsumoCatalogoDto(
                otroInsumoId, "ARENA-1", "Arena lavada", "M3",
                new BigDecimal("50.000"), true, OffsetDateTime.now(ZoneOffset.UTC)));

        PresupuestoRequest reqA = new PresupuestoRequest(
                "ESC-A", "Escenario Base", "Cliente A",
                new BigDecimal("8.00"), new BigDecimal("3.00"), new BigDecimal("5.00"),
                null, null, null,
                List.of(new CapituloInicialRequest("CAP-01", "Estructura",
                        List.of(new ItemCreateRequest(insumoId, new BigDecimal("10.000")))))
        );

        PresupuestoDetalleResponse a = presupuestoService.crearPresupuesto(reqA, "sub-ana-01");
        PresupuestoDetalleResponse b = presupuestoService.duplicarComoEscenario(
                a.id(), new co.edu.uceva.presupuestoservice.dto.DuplicarEscenarioRequest("ESC-B", "Escenario Optimizado", null),
                "sub-ana-01");
        presupuestoService.crearPresupuesto(new PresupuestoRequest(
                        "SIN-ACERO", "Otro Proyecto", "Cliente B",
                        new BigDecimal("8.00"), new BigDecimal("3.00"), new BigDecimal("5.00"),
                        null, null, null,
                        List.of(new CapituloInicialRequest("CAP-01", "Preliminares",
                                List.of(new ItemCreateRequest(otroInsumoId, new BigDecimal("4.000")))))),
                "sub-ana-01");

        assertThat(b.presupuestoBaseId()).isEqualTo(a.id());

        UUID capBId = b.capitulos().get(0).id();
        UUID itemBId = b.capitulos().get(0).items().get(0).id();
        presupuestoService.actualizarItem(b.id(), capBId, itemBId,
                new co.edu.uceva.presupuestoservice.dto.ItemUpdateRequest(
                        new BigDecimal("15.000"), new BigDecimal("100.000")));

        // Verificar que A no fue alterado al modificar B
        PresupuestoDetalleResponse aReleido = presupuestoService.obtenerDetalle(a.id());
        assertThat(aReleido.costoDirecto()).isEqualByComparingTo("1000.00");

        var comparacion = presupuestoService.compararEscenarios(a.id(), b.id());
        assertThat(comparacion.escenarioA().costoDirecto()).isEqualByComparingTo("1000.00");
        assertThat(comparacion.escenarioB().costoDirecto()).isEqualByComparingTo("1500.00");
        assertThat(comparacion.comparacionTotal().diferenciaCostoDirectoAbsoluta()).isEqualByComparingTo("500.00");
        assertThat(comparacion.comparacionTotal().diferenciaCostoDirectoPorcentual()).isEqualByComparingTo("50.00");

        mockMvc.perform(get("/api/v1/presupuestos/por-insumo/{insumoId}", insumoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].codigo", org.hamcrest.Matchers.containsInAnyOrder("ESC-A", "ESC-B")));
    }

    @Test
    @DisplayName("UC-26 y UC-61 (RQ-01): Guardar, recuperar y conciliar 200 items en 5 capitulos con desviacion $0.00")
    void guardarRecuperarYConciliarHojaControl200Items() throws Exception {
        Path csvPath = resolverRutaHojaControl();
        assertThat(Files.exists(csvPath)).isTrue();

        Map<String, String> nombreCapitulos = new LinkedHashMap<>();
        Map<String, List<ItemCreateRequest>> itemsPorCap = new LinkedHashMap<>();
        List<BigDecimal> parcialesEsperadosEnOrden = new ArrayList<>();
        List<BigDecimal> preciosEsperadosEnOrden = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = line.split(",");
                String capId = cols[2];
                String capNombre = cols[3];
                String codInsumo = cols[4];
                String desc = cols[5];
                String unidad = cols[6];
                BigDecimal cantidad = new BigDecimal(cols[7]);
                BigDecimal precioUnitario = new BigDecimal(cols[8]);
                BigDecimal parcialEsperado = new BigDecimal(cols[10]);

                UUID insumoId = UUID.randomUUID();
                catalogoEnMemoria.put(insumoId, new InsumoCatalogoDto(
                        insumoId, codInsumo, desc, unidad, precioUnitario, true, OffsetDateTime.now(ZoneOffset.UTC)));

                nombreCapitulos.putIfAbsent(capId, capNombre);
                itemsPorCap.computeIfAbsent(capId, k -> new ArrayList<>())
                        .add(new ItemCreateRequest(insumoId, cantidad));
                preciosEsperadosEnOrden.add(precioUnitario);
                parcialesEsperadosEnOrden.add(parcialEsperado);
            }
        }

        List<CapituloInicialRequest> capitulosReq = new ArrayList<>();
        for (var entry : itemsPorCap.entrySet()) {
            capitulosReq.add(new CapituloInicialRequest(
                    entry.getKey(), nombreCapitulos.get(entry.getKey()), entry.getValue()));
        }

        PresupuestoRequest request = new PresupuestoRequest(
                "PRE-200", "Obra Hoja Control", "Interventoria UCEVA",
                new BigDecimal("8.00"), new BigDecimal("3.00"), new BigDecimal("5.00"),
                null, null, null,
                capitulosReq
        );

        PresupuestoDetalleResponse creado = presupuestoService.crearPresupuesto(request, "sub-ana-01");
        PresupuestoDetalleResponse recuperado = presupuestoService.obtenerDetalle(creado.id());

        assertThat(recuperado.capitulos()).hasSize(5);
        int idx = 0;
        for (var cap : recuperado.capitulos()) {
            for (var item : cap.items()) {
                assertThat(item.precioUnitarioCopia()).isEqualTo(preciosEsperadosEnOrden.get(idx));
                assertThat(item.valorParcial()).isEqualTo(parcialesEsperadosEnOrden.get(idx));
                idx++;
            }
        }
        assertThat(idx).isEqualTo(200);

        Map<String, BigDecimal> subtotalesEsperados = Map.of(
                "CAP-01", new BigDecimal("891086.71"),
                "CAP-02", new BigDecimal("1055433.36"),
                "CAP-03", new BigDecimal("1236102.03"),
                "CAP-04", new BigDecimal("879509.60"),
                "CAP-05", new BigDecimal("1149302.01")
        );
        for (var cap : recuperado.capitulos()) {
            assertThat(cap.subtotalCapitulo()).isEqualTo(subtotalesEsperados.get(cap.codigo()));
        }

        assertThat(recuperado.costoDirecto()).isEqualTo(new BigDecimal("5211433.71"));
        assertThat(recuperado.valorAdministracion()).isEqualTo(new BigDecimal("416914.70"));
        assertThat(recuperado.valorImprevistos()).isEqualTo(new BigDecimal("156343.01"));
        assertThat(recuperado.valorUtilidad()).isEqualTo(new BigDecimal("260571.69"));
        assertThat(recuperado.precioVenta()).isEqualTo(new BigDecimal("6045263.11"));
        assertThat(recuperado.margenSobreCostoPorcentaje()).isEqualTo(new BigDecimal("16.00"));
        assertThat(recuperado.margenSobrePrecioVentaPorcentaje()).isEqualTo(new BigDecimal("13.79"));
    }

    @Test
    @DisplayName("UC-40 y UC-41: Sensibilidad e insumos criticos delegan al motor de calculo sin alterar el presupuesto")
    void sensibilidadEInsumosCriticosNoModificanPresupuesto() throws Exception {
        UUID insumoId = UUID.randomUUID();
        catalogoEnMemoria.put(insumoId, new InsumoCatalogoDto(
                insumoId, "CEM-01", "Cemento", "BULTO",
                new BigDecimal("100.000"), true, OffsetDateTime.now(ZoneOffset.UTC)));

        PresupuestoDetalleResponse creado = presupuestoService.crearPresupuesto(
                new PresupuestoRequest("PRE-SEN", "Sensibilidad", "Cliente",
                        new BigDecimal("8.00"), new BigDecimal("3.00"), new BigDecimal("5.00"),
                        null, null, null,
                        List.of(new CapituloInicialRequest("C1", "Cap 1",
                                List.of(new ItemCreateRequest(insumoId, new BigDecimal("10.000")))))),
                "sub-ana-01");

        given(calculoMotorClient.simularSensibilidad(any())).willReturn(
                new co.edu.uceva.presupuestoservice.dto.CalculoDtos.SimulacionSensibilidadResponse(
                        "CEM-01", new BigDecimal("10.00"),
                        new BigDecimal("1000.00"), new BigDecimal("1100.00"),
                        new BigDecimal("100.00"), new BigDecimal("10.00"),
                        new BigDecimal("1160.00"), new BigDecimal("1276.00"),
                        new BigDecimal("116.00"), new BigDecimal("10.00"),
                        null, null, null));

        given(calculoMotorClient.identificarInsumosCriticos(any())).willReturn(
                new co.edu.uceva.presupuestoservice.dto.CalculoDtos.InsumosCriticosResponse(
                        new BigDecimal("1000.00"), new BigDecimal("1160.00"), new BigDecimal("10.00"),
                        List.of(new co.edu.uceva.presupuestoservice.dto.CalculoDtos.InsumosCriticosResponse.InsumoCriticoItem(
                                "CEM-01", "Cemento", "BULTO", new BigDecimal("10.000"),
                                new BigDecimal("1000.00"), new BigDecimal("100.00"),
                                new BigDecimal("100.00"), new BigDecimal("116.00")))));

        mockMvc.perform(post("/api/v1/presupuestos/{id}/sensibilidad", creado.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoInsumo\":\"CEM-01\",\"porcentajeVariacion\":10.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diferenciaCostoDirecto").value(100.00));

        mockMvc.perform(get("/api/v1/presupuestos/{id}/insumos-criticos", creado.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insumosCriticos[0].codigoInsumo").value("CEM-01"));

        PresupuestoDetalleResponse intacto = presupuestoService.obtenerDetalle(creado.id());
        assertThat(intacto.capitulos().get(0).items().get(0).precioUnitarioCopia())
                .isEqualTo(new BigDecimal("100.000"));
    }

    private static CalculoPresupuestoResponse ejecutarProtocoloCalculoExacto(CalculoPresupuestoRequest req) {
        BigDecimal cero = new BigDecimal("0.00");
        BigDecimal cien = new BigDecimal("100");
        List<CapituloCalculado> capsOut = new ArrayList<>();
        BigDecimal cd = cero;

        for (var cap : req.capitulos()) {
            List<ItemCalculado> itemsOut = new ArrayList<>();
            BigDecimal subCap = cero;
            for (var item : cap.items()) {
                BigDecimal q = item.cantidad().setScale(3, RoundingMode.HALF_UP);
                BigDecimal p = item.precioUnitario().setScale(3, RoundingMode.HALF_UP);
                BigDecimal parcial = q.multiply(p).setScale(2, RoundingMode.HALF_UP);
                subCap = subCap.add(parcial);
                itemsOut.add(new ItemCalculado(item.itemId(), item.codigoInsumo(), q, p, parcial));
            }
            cd = cd.add(subCap);
            capsOut.add(new CapituloCalculado(cap.capituloId(), cap.nombre(), subCap, itemsOut));
        }

        BigDecimal pA = req.porcentajeAdministracion().setScale(2, RoundingMode.HALF_UP);
        BigDecimal pI = req.porcentajeImprevistos().setScale(2, RoundingMode.HALF_UP);
        BigDecimal pU = req.porcentajeUtilidad().setScale(2, RoundingMode.HALF_UP);
        BigDecimal vA = cd.multiply(pA).divide(cien, 2, RoundingMode.HALF_UP);
        BigDecimal vI = cd.multiply(pI).divide(cien, 2, RoundingMode.HALF_UP);
        BigDecimal vU = cd.multiply(pU).divide(cien, 2, RoundingMode.HALF_UP);
        BigDecimal pv = cd.add(vA).add(vI).add(vU);
        BigDecimal ganancia = pv.subtract(cd);
        BigDecimal mc = cd.compareTo(BigDecimal.ZERO) > 0
                ? ganancia.multiply(cien).divide(cd, 2, RoundingMode.HALF_UP) : cero;
        BigDecimal mv = pv.compareTo(BigDecimal.ZERO) > 0
                ? ganancia.multiply(cien).divide(pv, 2, RoundingMode.HALF_UP) : cero;

        return new CalculoPresupuestoResponse(
                capsOut, cd, pA, vA, pI, vI, pU, vU, pv, mc,
                "Margen sobre el costo (Markup = (PV - CD) / CD)",
                mv,
                "Margen sobre el precio de venta (Gross Margin = (PV - CD) / PV)",
                new PuntoEquilibrioResult(true, BigDecimal.ZERO, BigDecimal.ZERO, "")
        );
    }

    private static String extraerCampoJson(String json, String campo) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get(campo).asText();
    }

    private static Jwt construirJwt(String sub, String rol) {
        return Jwt.withTokenValue("token-" + rol)
                .header("alg", "RS256")
                .subject(sub)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("realm_access", Map.of("roles", List.of(rol)))
                .build();
    }

    private static Path resolverRutaHojaControl() {
        Path p1 = Paths.get("docs/control/hoja_control_200_items.csv");
        if (Files.exists(p1)) return p1;
        Path p2 = Paths.get("../../docs/control/hoja_control_200_items.csv");
        if (Files.exists(p2)) return p2;
        return p1;
    }
}
