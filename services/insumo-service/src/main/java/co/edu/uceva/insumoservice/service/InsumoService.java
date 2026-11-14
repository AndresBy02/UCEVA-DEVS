package co.edu.uceva.insumoservice.service;

import co.edu.uceva.insumoservice.api.dto.InsumoRequest;
import co.edu.uceva.insumoservice.domain.CodigoDuplicadoException;
import co.edu.uceva.insumoservice.domain.InsumoNoEncontradoException;
import co.edu.uceva.insumoservice.domain.UnidadMedida;
import co.edu.uceva.insumoservice.domain.UnidadMedidaParser;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.repository.InsumoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    public InsumoService(InsumoRepository insumoRepository) {
        this.insumoRepository = insumoRepository;
    }

    /**
     * Registra un nuevo insumo en el catalogo.
     *
     * @param request datos validados de entrada
     * @return la entidad persistida
     * @throws CodigoDuplicadoException      si ya existe un insumo con ese codigo
     * @throws co.edu.uceva.insumoservice.domain.UnidadMedidaInvalidaException
     *                                       si la unidad de medida no es valida
     */
    @Transactional
    public Insumo crear(InsumoRequest request) {
        String codigo = request.getCodigo().trim();
        if (insumoRepository.existsByCodigo(codigo)) {
            throw new CodigoDuplicadoException(codigo);
        }

        UnidadMedida unidadMedida = UnidadMedidaParser.parse(request.getUnidadMedida());
        BigDecimal precioUnitario = normalizarPrecio(request.getPrecioUnitario());

        Insumo insumo = new Insumo();
        insumo.setCodigo(codigo);
        insumo.setDescripcion(request.getDescripcion().trim());
        insumo.setUnidadMedida(unidadMedida);
        insumo.setPrecioUnitario(precioUnitario);
        insumo.setActivo(true);
        insumo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return insumoRepository.save(insumo);
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
