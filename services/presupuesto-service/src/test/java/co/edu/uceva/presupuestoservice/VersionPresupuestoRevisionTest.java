package co.edu.uceva.presupuestoservice;

import co.edu.uceva.presupuestoservice.client.CalculoMotorClient;
import co.edu.uceva.presupuestoservice.client.InsumoCatalogoClient;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:presupuesto_versiones_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class VersionPresupuestoRevisionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsumoCatalogoClient insumoCatalogoClient;

    @MockitoBean
    private CalculoMotorClient calculoMotorClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void configurarMocks() {
        given(jwtDecoder.decode("token-presupuestador")).willReturn(construirJwt("sub-ana-01", "presupuestador"));
        given(jwtDecoder.decode("token-revisor")).willReturn(construirJwt("sub-carlos-02", "revisor"));
        given(calculoMotorClient.calcularPresupuesto(any(CalculoPresupuestoRequest.class)))
                .willReturn(calculoFijo());
    }

    @Test
    @DisplayName("UC-38 / HU-17 / RF-10: enviar a revisión congela una versión, notifica al revisor y la versión no se modifica")
    void enviarARevisionCongelaVersionInmutableYListaVersiones() throws Exception {
        String id = crearPresupuesto();

        String snapshotPrimeraVersion = mockMvc.perform(post("/api/v1/presupuestos/" + id + "/enviar-revision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinatarioSub":"sub-carlos-02","mensaje":"Revisar antes de enviar al cliente"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.estado").value("ENVIADA"))
                .andExpect(jsonPath("$.creadoPor").value("sub-ana-01"))
                .andExpect(jsonPath("$.destinatarioSub").value("sub-carlos-02"))
                .andExpect(jsonPath("$.fechaNotificacion").exists())
                .andExpect(jsonPath("$.snapshotJson").value(org.hamcrest.Matchers.containsString("REV-001")))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/presupuestos/" + id + "/versiones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].destinatarioSub").value("sub-carlos-02"));

        mockMvc.perform(put("/api/v1/presupuestos/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Presupuesto modificado despues del envio","cliente":"Cliente"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/presupuestos/" + id + "/versiones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].snapshotJson").value(snapshotJsonDe(snapshotPrimeraVersion)))
                .andExpect(jsonPath("$[0].nombrePresupuesto").value("Edificio UCEVA"));

        mockMvc.perform(post("/api/v1/presupuestos/" + id + "/enviar-revision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.destinatarioSub").value("revisor"));

        mockMvc.perform(get("/api/v1/presupuestos/" + id + "/versiones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numero").value(2))
                .andExpect(jsonPath("$[1].numero").value(1));

        mockMvc.perform(get("/api/v1/presupuestos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalVersiones").value(2));
    }

    @Test
    @DisplayName("UC-38: solo el perfil presupuestador puede enviar a revisión")
    void revisorNoPuedeEnviarARevision() throws Exception {
        String id = crearPresupuesto();

        mockMvc.perform(post("/api/v1/presupuestos/" + id + "/enviar-revision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private String crearPresupuesto() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/presupuestos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"REV-001","nombre":"Edificio UCEVA","cliente":"Municipio Tulua"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(respuesta, "$.id");
    }

    private static String snapshotJsonDe(String respuestaVersion) {
        return com.jayway.jsonpath.JsonPath.read(respuestaVersion, "$.snapshotJson");
    }

    private static CalculoPresupuestoResponse calculoFijo() {
        return new CalculoPresupuestoResponse(
                List.of(),
                new BigDecimal("100.00"),
                new BigDecimal("8.0000"),
                new BigDecimal("8.00"),
                new BigDecimal("3.0000"),
                new BigDecimal("3.00"),
                new BigDecimal("5.0000"),
                new BigDecimal("5.00"),
                new BigDecimal("116.00"),
                new BigDecimal("16.00"),
                "Margen sobre el costo",
                new BigDecimal("13.79"),
                "Margen sobre precio de venta",
                null
        );
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
}
