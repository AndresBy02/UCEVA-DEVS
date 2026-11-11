package co.edu.uceva.insumoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import co.edu.uceva.insumoservice.dto.InsumoRequest;
import co.edu.uceva.insumoservice.model.HistorialPrecioInsumo;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.model.UnidadMedida;
import co.edu.uceva.insumoservice.repository.HistorialPrecioRepository;
import co.edu.uceva.insumoservice.repository.InsumoRepository;

class InsumoCommandServiceTest {
    private final InsumoRepository insumoRepository = org.mockito.Mockito.mock(InsumoRepository.class);
    private final HistorialPrecioRepository historialRepository = org.mockito.Mockito.mock(HistorialPrecioRepository.class);
    private final InsumoCommandService service = new InsumoCommandService(insumoRepository, historialRepository);

    private final UUID insumoId = UUID.randomUUID();
    private Insumo insumo;

    @BeforeEach
    void setUp() {
        insumo = new Insumo();
        ReflectionTestUtils.setField(insumo, "id", insumoId);
        insumo.setCodigo("CEM-001");
        insumo.setDescripcion("Cemento gris");
        insumo.setUnidadMedida(UnidadMedida.BULTO);
        insumo.setPrecioUnitario(new BigDecimal("32000.000"));
        insumo.setActivo(true);
        insumo.setUpdatedAt(OffsetDateTime.now());
        when(insumoRepository.findByIdForUpdate(insumoId)).thenReturn(java.util.Optional.of(insumo));
        when(insumoRepository.save(any(Insumo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historialRepository.save(any(HistorialPrecioInsumo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registraLosDosCambiosDePrecioEnOrdenConSuPrecioAnterior() {
        service.actualizar(insumoId, request("33000.000"), "presupuestador1 (subject-1)");
        service.actualizar(insumoId, request("34500.000"), "presupuestador1 (subject-1)");

        ArgumentCaptor<HistorialPrecioInsumo> captor = ArgumentCaptor.forClass(HistorialPrecioInsumo.class);
        verify(historialRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(HistorialPrecioInsumo::getPrecioAnterior, HistorialPrecioInsumo::getPrecioNuevo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(new BigDecimal("32000.000"), new BigDecimal("33000.000")),
                        org.assertj.core.groups.Tuple.tuple(new BigDecimal("33000.000"), new BigDecimal("34500.000")));
        assertThat(insumo.getPrecioUnitario()).isEqualByComparingTo("34500.000");
        assertThat(captor.getAllValues()).allSatisfy(entry -> {
            assertThat(entry.getInsumoId()).isEqualTo(insumoId);
            assertThat(entry.getModificadoPor()).isEqualTo("presupuestador1 (subject-1)");
        });
        verify(insumoRepository, times(2)).save(insumo);
    }

    @Test
    void noRegistraHistorialCuandoElPrecioNoCambia() {
        service.actualizar(insumoId, request("32000.000"), "sub-1");

        verify(historialRepository, never()).save(any(HistorialPrecioInsumo.class));
    }

    @Test
    void rechazaPrecioConMasDeTresDecimalesAntesDeEscribir() {
        assertThatThrownBy(() -> service.actualizar(insumoId, request("32000.0001"), "sub-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(insumoRepository, never()).save(any(Insumo.class));
        verify(historialRepository, never()).save(any(HistorialPrecioInsumo.class));
    }

    private InsumoRequest request(String precio) {
        return new InsumoRequest("Cemento gris", UnidadMedida.BULTO, new BigDecimal(precio), true);
    }
}
