package co.edu.uceva.insumoservice.api;

import co.edu.uceva.insumoservice.api.dto.InsumoRequest;
import co.edu.uceva.insumoservice.config.SecurityConfig;
import co.edu.uceva.insumoservice.domain.UnidadMedida;
import co.edu.uceva.insumoservice.model.HistorialPrecioInsumo;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.service.InsumoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InsumoController.class)
@Import(SecurityConfig.class)
class InsumoSeguridadWebTest {

    private static final String BODY_VALIDO = """
            {"codigo":"CEM-01","descripcion":"Cemento gris","unidadMedida":"BULTO","precioUnitario":"32500.500"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsumoService insumoService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("UC-52: Sin token o con firma alterada responde 401")
    void sinTokenOFirmaAlteradaResponde401() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/insumos")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/insumos").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/insumos/{id}", id).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isUnauthorized());

        given(jwtDecoder.decode("token-firma-alterada")).willThrow(new BadJwtException("Firma alterada"));
        mockMvc.perform(get("/api/v1/insumos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-firma-alterada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("UC-53: Rol revisor recibe 403 al intentar crear o editar un insumo y 200 al consultar")
    void revisorRecibe403EnEscrituraY200EnLectura() throws Exception {
        UUID id = UUID.randomUUID();
        given(jwtDecoder.decode("token-revisor")).willReturn(construirJwt("sub-revisor-1", "revisor"));

        mockMvc.perform(post("/api/v1/insumos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/insumos/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden());

        given(insumoService.obtenerPorId(id)).willReturn(crearEntidad(id, "sub-presupuestador-1"));
        mockMvc.perform(get("/api/v1/insumos/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioUnitario").value("32500.500"));
    }

    @Test
    @DisplayName("UC-20, UC-53, UC-54: Presupuestador crea/actualiza guardando claim sub y consulta historial")
    void presupuestadorCreaActualizaYConsultaHistorial() throws Exception {
        UUID id = UUID.randomUUID();
        String sub = "sub-presupuestador-99";
        given(jwtDecoder.decode("token-presupuestador")).willReturn(construirJwt(sub, "presupuestador"));

        Insumo entidad = crearEntidad(id, sub);
        given(insumoService.crear(any(InsumoRequest.class), eq(sub))).willReturn(entidad);
        given(insumoService.actualizar(eq(id), any(InsumoRequest.class), eq(sub))).willReturn(entidad);

        HistorialPrecioInsumo hist = new HistorialPrecioInsumo();
        hist.setId(UUID.randomUUID());
        hist.setInsumoId(id);
        hist.setPrecioAnterior(new BigDecimal("30000.000"));
        hist.setPrecioNuevo(new BigDecimal("32500.500"));
        hist.setModificadoPor(sub);
        hist.setFechaCambio(OffsetDateTime.now(ZoneOffset.UTC));
        given(insumoService.obtenerHistorialPrecios(id)).willReturn(List.of(hist));

        mockMvc.perform(post("/api/v1/insumos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.updatedBy").value(sub));

        mockMvc.perform(put("/api/v1/insumos/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value(sub));

        mockMvc.perform(get("/api/v1/insumos/{id}/historial-precios", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].precioAnterior").value("30000.000"))
                .andExpect(jsonPath("$[0].precioNuevo").value("32500.500"))
                .andExpect(jsonPath("$[0].modificadoPor").value(sub));

        verify(insumoService).crear(any(InsumoRequest.class), eq(sub));
        verify(insumoService).actualizar(eq(id), any(InsumoRequest.class), eq(sub));
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

    private static Insumo crearEntidad(UUID id, String updatedBy) {
        Insumo insumo = new Insumo();
        insumo.setId(id);
        insumo.setCodigo("CEM-01");
        insumo.setDescripcion("Cemento gris");
        insumo.setUnidadMedida(UnidadMedida.BULTO);
        insumo.setPrecioUnitario(new BigDecimal("32500.500"));
        insumo.setActivo(true);
        insumo.setUpdatedBy(updatedBy);
        insumo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return insumo;
    }
}
