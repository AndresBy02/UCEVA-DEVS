package co.edu.uceva.insumoservice.service;

import co.edu.uceva.insumoservice.api.dto.InsumoRequest;
import co.edu.uceva.insumoservice.domain.CodigoDuplicadoException;
import co.edu.uceva.insumoservice.domain.InsumoNoEncontradoException;
import co.edu.uceva.insumoservice.domain.UnidadMedida;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.repository.InsumoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pruebas unitarias del flujo de guardado de {@link InsumoService} enfocadas en
 * el criterio CR-01 de la subtarea UC-19: el precio unitario se normaliza a
 * escala 3 exacta sin pasar por {@code double} y sin redondeos silenciosos.
 *
 * <p>Se usa JUnit 5 + Mockito puro (sin contexto de Spring y sin base de datos),
 * de modo que las pruebas se ejecutan en cualquier entorno sin Docker.</p>
 */
@ExtendWith(MockitoExtension.class)
class InsumoServicePrecioExactoTest {

    @Mock
    private InsumoRepository insumoRepository;

    @InjectMocks
    private InsumoService insumoService;

    /**
     * CR-01 (capa de servicio): el precio {@code 12.345} se guarda con valor y
     * escala exactos, sin notacion cientifica ni perdida de precision.
     */
    @Test
    @DisplayName("crear guarda el precio 12.345 con escala 3 exacta")
    void crearGuardaElPrecioConEscalaTresExacta() {
        given(insumoRepository.existsByCodigo("INS-001")).willReturn(false);
        given(insumoRepository.save(any(Insumo.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        Insumo creado = insumoService.crear(solicitud("INS-001", "kg", "12.345"));

        ArgumentCaptor<Insumo> captor = ArgumentCaptor.forClass(Insumo.class);
        verify(insumoRepository).save(captor.capture());
        Insumo guardado = captor.getValue();

        // BigDecimal.equals compara tambien la escala: 12.345 != 12.3450.
        assertThat(guardado.getPrecioUnitario()).isEqualTo(new BigDecimal("12.345"));
        assertThat(guardado.getPrecioUnitario().scale()).isEqualTo(3);
        assertThat(guardado.getPrecioUnitario()).isEqualByComparingTo("12.345");
        assertThat(guardado.getPrecioUnitario().toPlainString()).isEqualTo("12.345");

        assertThat(guardado.getCodigo()).isEqualTo("INS-001");
        assertThat(guardado.getUnidadMedida()).isEqualTo(UnidadMedida.KG);
        assertThat(guardado.isActivo()).isTrue();
        assertThat(creado.getPrecioUnitario()).isEqualTo(new BigDecimal("12.345"));
    }

    /**
     * Un precio recibido con un cero final ({@code "12.3450"}, escala 4) se
     * normaliza a la escala 3 exacta sin alterar su valor.
     */
    @Test
    @DisplayName("crear normaliza \"12.3450\" (escala 4 con cero final) a 12.345 con escala 3")
    void crearNormalizaLaEscalaSinAlterarElValor() {
        given(insumoRepository.existsByCodigo("INS-001")).willReturn(false);
        given(insumoRepository.save(any(Insumo.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        insumoService.crear(solicitud("INS-001", "kg", "12.3450"));

        ArgumentCaptor<Insumo> captor = ArgumentCaptor.forClass(Insumo.class);
        verify(insumoRepository).save(captor.capture());
        BigDecimal precioGuardado = captor.getValue().getPrecioUnitario();

        assertThat(precioGuardado).isEqualTo(new BigDecimal("12.345"));
        assertThat(precioGuardado.toString()).isEqualTo("12.345");
        assertThat(precioGuardado.scale()).isEqualTo(3);
    }

    /**
     * Defensa del servicio: si llegara un precio con mas de tres decimales (por
     * ejemplo porque se invoca el servicio sin pasar por la validacion del DTO),
     * la normalizacion falla de forma explicita en lugar de redondear o truncar,
     * y no se persiste nada.
     */
    @Test
    @DisplayName("crear falla de forma explicita con mas de 3 decimales y no guarda")
    void crearNoRedondeaEnSilencioConMasDeTresDecimales() {
        given(insumoRepository.existsByCodigo("INS-001")).willReturn(false);

        assertThatThrownBy(() -> insumoService.crear(solicitud("INS-001", "kg", "12.3456")))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("Rounding necessary");

        verify(insumoRepository, never()).save(any(Insumo.class));
    }

    /**
     * Un codigo ya registrado se rechaza con {@link CodigoDuplicadoException}
     * (409 en el manejador global) y no se intenta guardar el insumo.
     */
    @Test
    @DisplayName("crear con código duplicado lanza CodigoDuplicadoException y no guarda")
    void crearConCodigoDuplicadoNoGuarda() {
        given(insumoRepository.existsByCodigo("INS-001")).willReturn(true);

        assertThatThrownBy(() -> insumoService.crear(solicitud("INS-001", "kg", "12.345")))
                .isInstanceOf(CodigoDuplicadoException.class)
                .hasMessageContaining("INS-001");

        verify(insumoRepository, never()).save(any(Insumo.class));
    }

    /**
     * Consultar un identificador inexistente produce
     * {@link InsumoNoEncontradoException} (404 en el manejador global).
     */
    @Test
    @DisplayName("obtenerPorId inexistente lanza InsumoNoEncontradoException")
    void obtenerPorIdInexistenteLanzaExcepcion() {
        UUID id = UUID.randomUUID();
        given(insumoRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> insumoService.obtenerPorId(id))
                .isInstanceOf(InsumoNoEncontradoException.class)
                .hasMessageContaining(id.toString());
    }

    /**
     * La lectura de un insumo conserva el precio exacto con escala 3, que es lo
     * que luego serializa la capa web como cadena {@code "12.345"}.
     */
    @Test
    @DisplayName("obtenerPorId conserva el precio exacto con escala 3")
    void obtenerPorIdConservaElPrecioExacto() {
        UUID id = UUID.randomUUID();
        Insumo almacenado = new Insumo();
        almacenado.setId(id);
        almacenado.setCodigo("INS-001");
        almacenado.setDescripcion("Cemento gris");
        almacenado.setUnidadMedida(UnidadMedida.KG);
        almacenado.setPrecioUnitario(new BigDecimal("12.345"));
        given(insumoRepository.findById(id)).willReturn(Optional.of(almacenado));

        Insumo encontrado = insumoService.obtenerPorId(id);

        assertThat(encontrado).isSameAs(almacenado);
        assertThat(encontrado.getPrecioUnitario()).isEqualTo(new BigDecimal("12.345"));
        assertThat(encontrado.getPrecioUnitario().scale()).isEqualTo(3);
    }

    /**
     * Construye una solicitud valida con el precio indicado como texto decimal
     * exacto (nunca a partir de un {@code double}).
     */
    private static InsumoRequest solicitud(String codigo, String unidadMedida, String precio) {
        InsumoRequest request = new InsumoRequest();
        request.setCodigo(codigo);
        request.setDescripcion("Cemento gris");
        request.setUnidadMedida(unidadMedida);
        request.setPrecioUnitario(new BigDecimal(precio));
        return request;
    }
}
