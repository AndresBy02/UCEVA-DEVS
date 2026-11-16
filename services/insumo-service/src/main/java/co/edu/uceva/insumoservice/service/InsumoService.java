package co.edu.uceva.insumoservice.service;

import co.edu.uceva.insumoservice.api.dto.InsumoRequest;
import co.edu.uceva.insumoservice.domain.CodigoDuplicadoException;
import co.edu.uceva.insumoservice.domain.InsumoNoEncontradoException;
import co.edu.uceva.insumoservice.domain.UnidadMedida;
import co.edu.uceva.insumoservice.domain.UnidadMedidaParser;
import co.edu.uceva.insumoservice.model.HistorialPrecioInsumo;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.repository.HistorialPrecioInsumoRepository;
import co.edu.uceva.insumoservice.repository.InsumoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Logica de negocio del catalogo de insumos.
 *
 * <p>Centraliza la resolucion de la unidad de medida (via
 * {@link UnidadMedidaParser}), la normalizacion exacta del precio a escala 3
 * (sin pasar nunca por {@code double}) y la validacion de unicidad del codigo.</p>
 */
@Service
public class InsumoService {

    /** Escala exacta del precio unitario: NUMERIC(19,3). */
    private static final int ESCALA_PRECIO = 3;

    private final InsumoRepository insumoRepository;
    private final HistorialPrecioInsumoRepository historialRepository;

    public InsumoService(InsumoRepository insumoRepository) {
        this(insumoRepository, null);
    }

    @Autowired
    public InsumoService(InsumoRepository insumoRepository,
                         HistorialPrecioInsumoRepository historialRepository) {
        this.insumoRepository = insumoRepository;
        this.historialRepository = historialRepository;
    }

    @Transactional
    public Insumo crear(InsumoRequest request) {
        return crear(request, null);
    }

    @Transactional
    public Insumo crear(InsumoRequest request, String usuarioSub) {
        String codigo = request.getCodigo().trim();
        if (insumoRepository.existsByCodigo(codigo)) {
            throw new CodigoDuplicadoException(codigo);
        }

        UnidadMedida unidadMedida = UnidadMedidaParser.parse(request.getUnidadMedida());
        BigDecimal precioUnitario = normalizarPrecio(request.getPrecioUnitario());
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);

        Insumo insumo = new Insumo();
        insumo.setCodigo(codigo);
        insumo.setDescripcion(request.getDescripcion().trim());
        insumo.setUnidadMedida(unidadMedida);
        insumo.setPrecioUnitario(precioUnitario);
        insumo.setActivo(true);
        insumo.setUpdatedBy(usuarioSub);
        insumo.setUpdatedAt(ahora);

        Insumo guardado = insumoRepository.save(insumo);
        if (historialRepository != null && guardado.getId() != null) {
            registrarCambioPrecio(guardado.getId(), null, precioUnitario, usuarioSub, ahora);
        }
        return guardado;
    }

    @Transactional
    public Insumo actualizar(UUID id, InsumoRequest request, String usuarioSub) {
        Insumo insumo = obtenerPorId(id);
        String codigo = request.getCodigo().trim();
        if (insumoRepository.existsByCodigoAndIdNot(codigo, id)) {
            throw new CodigoDuplicadoException(codigo);
        }

        UnidadMedida unidadMedida = UnidadMedidaParser.parse(request.getUnidadMedida());
        BigDecimal precioAnterior = insumo.getPrecioUnitario();
        BigDecimal precioNuevo = normalizarPrecio(request.getPrecioUnitario());
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);

        insumo.setCodigo(codigo);
        insumo.setDescripcion(request.getDescripcion().trim());
        insumo.setUnidadMedida(unidadMedida);
        insumo.setPrecioUnitario(precioNuevo);
        insumo.setUpdatedBy(usuarioSub);
        insumo.setUpdatedAt(ahora);

        Insumo actualizado = insumoRepository.save(insumo);
        if (historialRepository != null
                && (precioAnterior == null || precioAnterior.compareTo(precioNuevo) != 0)) {
            registrarCambioPrecio(actualizado.getId(), precioAnterior, precioNuevo, usuarioSub, ahora);
        }
        return actualizado;
    }

    @Transactional(readOnly = true)
    public List<HistorialPrecioInsumo> obtenerHistorialPrecios(UUID id) {
        obtenerPorId(id);
        return historialRepository != null
                ? historialRepository.findByInsumoIdOrderByFechaCambioDesc(id)
                : List.of();
    }

    private void registrarCambioPrecio(UUID insumoId, BigDecimal precioAnterior,
                                       BigDecimal precioNuevo, String modificadoPor,
                                       OffsetDateTime fechaCambio) {
        HistorialPrecioInsumo historial = new HistorialPrecioInsumo();
        historial.setInsumoId(insumoId);
        historial.setPrecioAnterior(precioAnterior);
        historial.setPrecioNuevo(precioNuevo);
        historial.setModificadoPor(modificadoPor);
        historial.setFechaCambio(fechaCambio);
        historialRepository.save(historial);
    }

    /**
     * Recupera un insumo por su identificador.
     *
     * @param id identificador del insumo
     * @return la entidad encontrada
     * @throws InsumoNoEncontradoException si no existe un insumo con ese id
     */
    @Transactional(readOnly = true)
    public Insumo obtenerPorId(UUID id) {
        return insumoRepository.findById(id)
                .orElseThrow(() -> new InsumoNoEncontradoException(id));
    }

    /**
     * Lista el catalogo de insumos de forma paginada.
     *
     * @param pageable pagina, tamano y orden solicitados
     * @return pagina de entidades
     */
    @Transactional(readOnly = true)
    public Page<Insumo> listar(Pageable pageable) {
        return insumoRepository.findAll(pageable);
    }

    /**
     * Normaliza el precio a escala 3 partiendo siempre de su representacion
     * decimal exacta (nunca de un {@code double}).
     *
     * <p>La validacion {@code @Digits(fraction = 3)} garantiza que no haya mas
     * de 3 decimales, por lo que {@link RoundingMode#UNNECESSARY} no redondea
     * en silencio: si llegara un valor con mas decimales, fallaria de forma
     * explicita en lugar de truncarlo.</p>
     */
    private static BigDecimal normalizarPrecio(BigDecimal precioUnitario) {
        return new BigDecimal(precioUnitario.toPlainString())
                .setScale(ESCALA_PRECIO, RoundingMode.UNNECESSARY);
    }
}
