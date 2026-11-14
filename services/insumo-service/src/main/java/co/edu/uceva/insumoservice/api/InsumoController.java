package co.edu.uceva.insumoservice.api;

import co.edu.uceva.insumoservice.api.dto.InsumoRequest;
import co.edu.uceva.insumoservice.api.dto.InsumoResponse;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.service.InsumoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * API REST del catalogo maestro de insumos.
 *
 * <p>Expone la ruta congelada {@code /api/v1/insumos}:
 * <ul>
 *   <li>{@code POST /api/v1/insumos} responde 201 con el header {@code Location};</li>
 *   <li>{@code GET /api/v1/insumos/{id}} responde 200 o 404;</li>
 *   <li>{@code GET /api/v1/insumos} responde 200 con la pagina de insumos.</li>
 * </ul>
 * El control de acceso por rol (presupuestador para escritura y cualquier
 * usuario autenticado para lectura) se aplica en {@code SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/v1/insumos")
public class InsumoController {

    /** Propiedades del insumo admitidas en el parametro {@code sort}. */
    private static final List<String> PROPIEDADES_ORDENABLES =
            List.of("codigo", "descripcion", "unidadMedida", "precioUnitario", "activo", "updatedAt");

    private final InsumoService insumoService;

    public InsumoController(InsumoService insumoService) {
        this.insumoService = insumoService;
    }

    /**
     * Crea un insumo en el catalogo.
     *
     * @param request datos del insumo a crear
     * @return 201 Created con el header {@code Location} y el insumo persistido
     */
    @PostMapping
    public ResponseEntity<InsumoResponse> crear(@Valid @RequestBody InsumoRequest request) {
        Insumo creado = insumoService.crear(request);
        URI location = URI.create("/api/v1/insumos/" + creado.getId());
        return ResponseEntity.created(location).body(InsumoMapper.toResponse(creado));
    }

    /**
     * Consulta un insumo por su identificador.
     *
     * @param id identificador UUID del insumo
     * @return 200 con el insumo encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<InsumoResponse> obtenerPorId(@PathVariable UUID id) {
        Insumo insumo = insumoService.obtenerPorId(id);
        return ResponseEntity.ok(InsumoMapper.toResponse(insumo));
    }

    /**
     * Lista el catalogo de insumos de forma paginada.
     *
     * @param page numero de pagina, base 0
     * @param size tamano de la pagina (1 a 100)
     * @param sort propiedad de ordenamiento ascendente (por defecto {@code codigo})
     * @return 200 con la pagina de insumos
     */
    @GetMapping
    public ResponseEntity<Page<InsumoResponse>> listar(
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo") int page,
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1")
            @Max(value = 100, message = "El tamaño de página no puede superar 100") int size,
            @RequestParam(name = "sort", defaultValue = "codigo") String sort) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, validarOrden(sort)));
        Page<InsumoResponse> pagina = InsumoMapper.toResponsePage(insumoService.listar(pageable));
        return ResponseEntity.ok(pagina);
    }

    /**
     * Valida que la propiedad de ordenamiento exista en la entidad, evitando
     * que un valor arbitrario provoque un error interno en el repositorio.
     */
    private static String validarOrden(String sort) {
        if (!PROPIEDADES_ORDENABLES.contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El parámetro \"sort\" debe ser una de las propiedades: "
                            + String.join(", ", PROPIEDADES_ORDENABLES));
        }
        return sort;
    }
}
