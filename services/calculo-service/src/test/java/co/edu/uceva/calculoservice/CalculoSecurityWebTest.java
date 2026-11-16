package co.edu.uceva.calculoservice;

import co.edu.uceva.calculoservice.config.SecurityConfig;
import co.edu.uceva.calculoservice.controller.CalculoController;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse;
import co.edu.uceva.calculoservice.service.FinanzasEvaluacionService;
import co.edu.uceva.calculoservice.service.PresupuestoCalculoService;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CalculoController.class)
@Import(SecurityConfig.class)
class CalculoSecurityWebTest {

    private static final String BODY_PRESUPUESTO = """
            {"porcentajeAdministracion":8.00,"porcentajeImprevistos":3.00,"porcentajeUtilidad":5.00,"capitulos":[]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PresupuestoCalculoService presupuestoCalculoService;

    @MockitoBean
    private FinanzasEvaluacionService finanzasEvaluacionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("UC-52: Sin token o con firma alterada responde 401")
    void sinTokenOFirmaAlteradaResponde401() throws Exception {
        mockMvc.perform(post("/api/v1/calculos/presupuesto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_PRESUPUESTO))
                .andExpect(status().isUnauthorized());

        given(jwtDecoder.decode("firma-alterada")).willThrow(new BadJwtException("Firma alterada"));
        mockMvc.perform(post("/api/v1/calculos/presupuesto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer firma-alterada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_PRESUPUESTO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("UC-53: Rol no autorizado recibe 403; presupuestador y revisor reciben 200")
    void controlAccesoPorRol() throws Exception {
        CalculoPresupuestoResponse dummy = new CalculoPresupuestoResponse(
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "", BigDecimal.ZERO, "", null);
        given(presupuestoCalculoService.calcularPresupuesto(any())).willReturn(dummy);

        given(jwtDecoder.decode("token-invitado")).willReturn(construirJwt("u0", "invitado"));
        given(jwtDecoder.decode("token-presupuestador")).willReturn(construirJwt("u1", "presupuestador"));
        given(jwtDecoder.decode("token-revisor")).willReturn(construirJwt("u2", "revisor"));

        mockMvc.perform(post("/api/v1/calculos/presupuesto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-invitado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_PRESUPUESTO))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/calculos/presupuesto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-presupuestador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_PRESUPUESTO))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/calculos/presupuesto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-revisor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_PRESUPUESTO))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/calculos/spike-jna")).andExpect(status().isOk());
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
