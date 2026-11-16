package co.edu.uceva.insumoservice.service;

import co.edu.uceva.insumoservice.api.dto.InsumoRequest;
import co.edu.uceva.insumoservice.model.HistorialPrecioInsumo;
import co.edu.uceva.insumoservice.model.Insumo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:insumo_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional
class InsumoHistorialPreciosIntegrationTest {

    @Autowired
    private InsumoService insumoService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("UC-20 y UC-54: Registrar y editar insumo guarda historial de precios e identidad sub")
    void registrarYEditarInsumoGuardaHistorialYSub() {
        InsumoRequest crearReq = new InsumoRequest();
        crearReq.setCodigo("ACERO-01");
        crearReq.setDescripcion("Acero corrugado 1/2");
        crearReq.setUnidadMedida("KG");
        crearReq.setPrecioUnitario(new BigDecimal("4500.125"));

        Insumo creado = insumoService.crear(crearReq, "user-sub-1");
        assertThat(creado.getUpdatedBy()).isEqualTo("user-sub-1");
        assertThat(creado.getPrecioUnitario()).isEqualTo(new BigDecimal("4500.125"));

        InsumoRequest editarReq = new InsumoRequest();
        editarReq.setCodigo("ACERO-01");
        editarReq.setDescripcion("Acero corrugado 1/2 reforzado");
        editarReq.setUnidadMedida("KG");
        editarReq.setPrecioUnitario(new BigDecimal("4850.375"));

        Insumo actualizado = insumoService.actualizar(creado.getId(), editarReq, "user-sub-2");
        assertThat(actualizado.getUpdatedBy()).isEqualTo("user-sub-2");
        assertThat(actualizado.getPrecioUnitario()).isEqualTo(new BigDecimal("4850.375"));

        List<HistorialPrecioInsumo> historial = insumoService.obtenerHistorialPrecios(creado.getId());
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getPrecioNuevo()).isEqualTo(new BigDecimal("4850.375"));
        assertThat(historial.get(0).getPrecioAnterior()).isEqualTo(new BigDecimal("4500.125"));
        assertThat(historial.get(0).getModificadoPor()).isEqualTo("user-sub-2");
        assertThat(historial.get(1).getPrecioNuevo()).isEqualTo(new BigDecimal("4500.125"));
        assertThat(historial.get(1).getModificadoPor()).isEqualTo("user-sub-1");
    }
}
