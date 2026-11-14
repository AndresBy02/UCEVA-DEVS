package co.edu.uceva.insumoservice.api;

import co.edu.uceva.insumoservice.api.dto.InsumoRequest;
import co.edu.uceva.insumoservice.domain.UnidadMedida;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.service.InsumoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de aceptacion de la subtarea UC-19 (criterio CR-01 de la HU-01):
 * el precio unitario debe viajar de ida y vuelta sin perdida de precision.
 *
 * <p>Se verifica que {@code 12.345} entra como cadena decimal, se convierte en
 * {@link BigDecimal} con escala 3 exacta y vuelve a salir serializado como la
 * cadena {@code "12.345"} (nunca como {@code 12.34}, {@code 12.345000001} ni
 * con notacion cientifica). No se compara el precio como {@code double}.</p>
 *
 * <p><strong>Autorizacion:</strong> esta prueba se centra exclusivamente en la
 * representacion exacta del dinero, por lo que los filtros de seguridad se
 * desactivan con {@link AutoConfigureMockMvc#addFilters() addFilters = false}
 * y se excluye la autoconfiguracion del resource server JWT (que intentaria
 * contactar Keycloak). Los escenarios de 401/403 por rol (incluido el 403 del
 * rol {@code revisor} al crear insumos) se cubren en HU-29 y quedan
 * deliberadamente fuera de esta historia.</p>
 *
 * <p>No se usa {@code @MockBean} porque esta deprecado en Spring Boot 3.4; en su
 * lugar se usa {@link MockitoBean}.</p>
 */
@WebMvcTest(controllers = InsumoController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class InsumoPrecioExactoWebTest {

    /** Cuerpo de solicitud valido usado como base en las pruebas. */
    private static final String PLANTILLA_SOLICITUD = """
            {"codigo":"%s","descripcion":"Cemento gris","unidadMedida":"kg","precioUnitario":"%s"}
            """;

    @Autowired
    private MockMvc mockMvc;

    /**
     * Servicio simulado: la prueba valida el contrato HTTP y el valor que
     * recibe el servicio, sin depender de la base de datos.
     */
    @MockitoBean
    private InsumoService insumoService;

    /**
     * CR-01: al crear un insumo con precio {@code 12.345}, la respuesta 201 debe
     * contener exactamente la cadena {@code "12.345"} y el servicio debe recibir
     * un {@link BigDecimal} con escala 3 y valor exacto.
     */
    @Test
    @DisplayName("POST devuelve 201 con precioUnitario exactamente \"12.345\" y escala 3")
    void crearDevuelveElPrecioExactoComoCadena() throws Exception {
        UUID id = UUID.randomUUID();
        given(insumoService.crear(any(InsumoRequest.class)))
                .willReturn(insumoPersistido(id, new BigDecimal("12.345")));

        MvcResult resultado = mockMvc.perform(post("/api/v1/insumos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLANTILLA_SOLICITUD.formatted("INS-001", "12.345")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/insumos/" + id))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.codigo").value("INS-001"))
                .andExpect(jsonPath("$.precioUnitario").isString())
                // Comparacion de cadenas: jamas se convierte el precio a double.
                .andExpect(jsonPath("$.precioUnitario").value("12.345"))
                .andReturn();

        String cuerpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo)
                .as("el precio debe serializarse como cadena JSON exacta, no como numero")
                .contains("\"precioUnitario\":\"12.345\"")
                .doesNotContain("12.345000001");

        ArgumentCaptor<InsumoRequest> captor = ArgumentCaptor.forClass(InsumoRequest.class);
        verify(insumoService).crear(captor.capture());
        BigDecimal precioRecibido = captor.getValue().getPrecioUnitario();

        assertThat(precioRecibido).isEqualTo(new BigDecimal("12.345"));
        assertThat(precioRecibido.scale()).isEqualTo(3);
        assertThat(precioRecibido).isEqualByComparingTo("12.345");
    }

    /**
     * CR-01b: al consultar un insumo ya persistido con escala 3, la respuesta 200
     * devuelve exactamente la misma cadena {@code "12.345"}.
     */
    @Test
    @DisplayName("GET devuelve 200 con el mismo precioUnitario exacto \"12.345\" (ida y vuelta)")
    void obtenerPorIdDevuelveElMismoPrecioExacto() throws Exception {
        UUID id = UUID.randomUUID();
        given(insumoService.obtenerPorId(id))
                .willReturn(insumoPersistido(id, new BigDecimal("12.345")));

        MvcResult resultado = mockMvc.perform(get("/api/v1/insumos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.unidadMedida").value("KG"))
                .andExpect(jsonPath("$.precioUnitario").isString())
                .andExpect(jsonPath("$.precioUnitario").value("12.345"))
                .andReturn();

        String cuerpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo).contains("\"precioUnitario\":\"12.345\"");
        assertThat(cuerpo)
                .as("no debe aparecer un precio truncado, redondeado ni con ruido de coma flotante")
                .doesNotContain("12.345000001")
                .doesNotContain("\"precioUnitario\":12.34")
                .doesNotContain("\"precioUnitario\":\"12.34\"");
    }

    /**
     * Caso negativo de redondeo: un precio con cuatro decimales se rechaza con
     * 400 antes de llegar al servicio, de modo que nunca se redondea en
     * silencio a escala 3.
     */
    @Test
    @DisplayName("POST con precio de 4 decimales responde 400 y no invoca al servicio")
    void crearRechazaPrecioConMasDeTresDecimales() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/insumos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLANTILLA_SOLICITUD.formatted("INS-002", "12.3456")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores[0].campo").value("precioUnitario"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(containsString("3 decimales")))
                .andReturn();

        verifyNoInteractions(insumoService);

        String cuerpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo)
                .as("el error no debe contener un valor redondeado silenciosamente")
                .doesNotContain("12.346")
                .doesNotContain("12.3456");
    }

    /**
     * Construye una entidad ya persistida con el precio indicado.
     */
    private static Insumo insumoPersistido(UUID id, BigDecimal precioUnitario) {
        Insumo insumo = new Insumo();
        insumo.setId(id);
        insumo.setCodigo("INS-001");
        insumo.setDescripcion("Cemento gris");
        insumo.setUnidadMedida(UnidadMedida.KG);
        insumo.setPrecioUnitario(precioUnitario);
        insumo.setActivo(true);
        insumo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return insumo;
    }
}
