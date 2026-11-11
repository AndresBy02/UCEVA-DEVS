package co.edu.uceva.insumoservice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.uceva.insumoservice.client.PresupuestoClient;
import co.edu.uceva.insumoservice.dto.PresupuestoUsoResponse;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.model.UnidadMedida;
import co.edu.uceva.insumoservice.repository.HistorialPrecioRepository;
import co.edu.uceva.insumoservice.repository.InsumoRepository;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class InsumoApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("insumo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InsumoRepository insumoRepository;

    @Autowired
    private HistorialPrecioRepository historialRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private PresupuestoClient presupuestoClient;

    private final UUID insumoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        historialRepository.deleteAll();
        insumoRepository.deleteAll();
        insumoRepository.save(new Insumo(insumoId, "CEM-001", "Cemento gris", UnidadMedida.BULTO,
                new BigDecimal("32000.000"), true, null, OffsetDateTime.now()));
        when(presupuestoClient.findByInsumo(eq(insumoId), anyString())).thenReturn(List.of());
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_insumo_history ON historial_precios_insumo");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_insumo_history()");
    }

    @Test
    void registraDosCambiosDePrecioExactosYDevuelveHistorialYPresupuestosEnPut() throws Exception {
        var presupuesto = new PresupuestoUsoResponse(
                UUID.randomUUID(), "PRE-001", "Ampliación", "BORRADOR");
        when(presupuestoClient.findByInsumo(eq(insumoId), anyString())).thenReturn(List.of(presupuesto));

        JsonNode primeraRespuesta = actualizar("33000.000", "presupuestador");
        JsonNode segundaRespuesta = actualizar("34500.000", "presupuestador");

        assertThatDecimal(segundaRespuesta.path("precioUnitario"), "34500.000");
        org.assertj.core.api.Assertions.assertThat(segundaRespuesta.path("historialPrecios")).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(segundaRespuesta.path("presupuestos").get(0).path("codigo").asText())
                .isEqualTo("PRE-001");
        assertThatDecimal(segundaRespuesta.path("historialPrecios").get(0).path("precioAnterior"), "33000.000");
        assertThatDecimal(segundaRespuesta.path("historialPrecios").get(0).path("precioNuevo"), "34500.000");
        assertThatDecimal(segundaRespuesta.path("historialPrecios").get(1).path("precioAnterior"), "32000.000");
        assertThatDecimal(segundaRespuesta.path("historialPrecios").get(1).path("precioNuevo"), "33000.000");
        org.assertj.core.api.Assertions.assertThat(segundaRespuesta.path("historialPrecios").get(0)
                .path("modificadoPor").asText()).isEqualTo("presupuestador1 (subject-estable)");
        org.assertj.core.api.Assertions.assertThat(
                insumoRepository.findById(insumoId).orElseThrow().getPrecioUnitario().scale()).isEqualTo(3);

        mockMvc.perform(get("/api/insumos/{id}/historial", insumoId).with(jwtFor("revisor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].precioNuevo").value(34500.0))
                .andExpect(jsonPath("$[1].precioNuevo").value(33000.0));
        mockMvc.perform(get("/api/insumos/{id}", insumoId).with(jwtFor("revisor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioUnitario").value(34500.0))
                .andExpect(jsonPath("$.presupuestos[0].codigo").value("PRE-001"));
        org.assertj.core.api.Assertions.assertThat(primeraRespuesta.path("historialPrecios")).hasSize(1);
    }

    @Test
    void persisteYDevuelveElPrecioConTresDecimalesSinRedondearlo() throws Exception {
        JsonNode response = actualizar("12.345", "presupuestador");

        assertThatDecimal(response.path("precioUnitario"), "12.345");
        BigDecimal precioPersistido = insumoRepository.findById(insumoId).orElseThrow().getPrecioUnitario();
        org.assertj.core.api.Assertions.assertThat(precioPersistido.toPlainString()).isEqualTo("12.345");
    }

    @Test
    void precioIgualNoCreaHistorialYPermiteCambiarDescripcionYUnidad() throws Exception {
        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor("presupuestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("32000.000", "Cemento actualizado", "KG", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Cemento actualizado"))
                .andExpect(jsonPath("$.unidadMedida").value("KG"))
                .andExpect(jsonPath("$.historialPrecios").isEmpty());

        org.assertj.core.api.Assertions.assertThat(historialRepository.count()).isZero();
        Insumo actualizado = insumoRepository.findById(insumoId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(actualizado.getDescripcion()).isEqualTo("Cemento actualizado");
        org.assertj.core.api.Assertions.assertThat(actualizado.getCodigo()).isEqualTo("CEM-001");
    }

    @Test
    void revierteElPrecioSiFallaElGuardadoDelHistorial() throws Exception {
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_insumo_history() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'fallo de prueba al guardar historial';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_insumo_history
                BEFORE INSERT ON historial_precios_insumo
                FOR EACH ROW EXECUTE FUNCTION reject_insumo_history()
                """);

        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor("presupuestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("33000.000", "Cemento gris", "BULTO", true)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value(containsString("error interno")));

        org.assertj.core.api.Assertions.assertThat(
                insumoRepository.findById(insumoId).orElseThrow().getPrecioUnitario())
                .isEqualByComparingTo("32000.000");
        org.assertj.core.api.Assertions.assertThat(historialRepository.count()).isZero();
    }

    @Test
    void rechazaAccesoSinTokenYProtegePutPorRol() throws Exception {
        mockMvc.perform(get("/api/insumos"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/insumos")
                        .header("Authorization", "Bearer token-malformado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(containsString("autenticación")));
        mockMvc.perform(get("/api/insumos")
                        .header("Authorization", "Bearer token-malformado"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("33000.000", "Cemento gris", "BULTO", true)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor("revisor"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("33000.000", "Cemento gris", "BULTO", true)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/insumos?activo=true").with(jwtFor("revisor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("CEM-001"));
        mockMvc.perform(get("/api/insumos?activo=false").with(jwtFor("revisor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/insumos").with(jwtFor("presupuestador")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor("presupuestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("33000.000", "Cemento gris", "BULTO", true)))
                .andExpect(status().isOk());
    }

    @Test
    void validaPrecioUnidadActivoYRechazaValoresInvalidos() throws Exception {
        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor("presupuestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Cemento","unidadMedida":"BULTO","precioUnitario":12.3456,"activo":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("máximo 3 decimales")));

        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor("presupuestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Cemento","unidadMedida":"BULTO","precioUnitario":-1.000,"activo":true}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor("presupuestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Cemento","unidadMedida":"BULTO","precioUnitario":12.345}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("activo")));

        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor("presupuestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Cemento","unidadMedida":"BOLSA","precioUnitario":12.345,"activo":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("M, M2, M3, KG, BULTO, UNIDAD, DIA")));

        mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor("presupuestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"OTRO","descripcion":"Cemento","unidadMedida":"BULTO","precioUnitario":12.345,"activo":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("campo desconocido")));
    }

    @Test
    void devuelve404CuandoElInsumoNoExisteEnGetYPut() throws Exception {
        UUID noExiste = UUID.randomUUID();
        mockMvc.perform(get("/api/insumos/{id}", noExiste).with(jwtFor("revisor")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Insumo no encontrado"));
        mockMvc.perform(put("/api/insumos/{id}", noExiste)
                        .with(jwtFor("presupuestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("33000.000", "Cemento gris", "BULTO", true)))
                .andExpect(status().isNotFound());
    }

    @Test
    void devuelve502SiPresupuestoServiceNoEstaDisponible() throws Exception {
        when(presupuestoClient.findByInsumo(eq(insumoId), anyString()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "No fue posible consultar los presupuestos asociados al insumo"));

        mockMvc.perform(get("/api/insumos/{id}", insumoId).with(jwtFor("presupuestador")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensaje").value(containsString("presupuestos asociados")));
    }

    private JsonNode actualizar(String precio, String role) throws Exception {
        String response = mockMvc.perform(put("/api/insumos/{id}", insumoId)
                        .with(jwtFor(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(precio, "Cemento gris", "BULTO", true)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String role) {
        return jwt()
                .jwt(token -> token.subject("subject-estable")
                        .claim("preferred_username", "presupuestador1")
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String requestBody(String precio, String descripcion, String unidad, boolean activo) {
        return """
                {"descripcion":"%s","unidadMedida":"%s","precioUnitario":%s,"activo":%s}
                """.formatted(descripcion, unidad, precio, activo);
    }

    private void assertThatDecimal(JsonNode node, String expected) {
        org.assertj.core.api.Assertions.assertThat(node.decimalValue())
                .isEqualByComparingTo(new BigDecimal(expected));
    }
}
