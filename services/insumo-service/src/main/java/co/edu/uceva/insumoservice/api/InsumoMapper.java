package co.edu.uceva.insumoservice.api;

import co.edu.uceva.insumoservice.api.dto.InsumoResponse;
import co.edu.uceva.insumoservice.model.Insumo;
import org.springframework.data.domain.Page;

/**
 * Mapeo entre la entidad {@link Insumo} y su DTO de salida {@link InsumoResponse}.
 *
 * <p>Se mantiene como clase utilitaria para no filtrar la entidad JPA hacia la
 * capa HTTP y para que el formato de {@code unidadMedida} sea siempre el codigo
 * del enum (por ejemplo {@code "M2"}).</p>
 */
public final class InsumoMapper {

    private InsumoMapper() {
        // Clase utilitaria: no se instancia
    }

    /**
     * Convierte una entidad en su representacion de salida.
     *
     * @param insumo entidad persistida (no debe ser {@code null})
     * @return el DTO con el codigo de la unidad de medida y el precio exacto
     */
    public static InsumoResponse toResponse(Insumo insumo) {
        return new InsumoResponse(
                insumo.getId(),
                insumo.getCodigo(),
                insumo.getDescripcion(),
                insumo.getUnidadMedida() != null ? insumo.getUnidadMedida().name() : null,
                insumo.getPrecioUnitario(),
                insumo.isActivo(),
                insumo.getUpdatedAt());
    }

    /**
     * Convierte una pagina de entidades en una pagina de DTOs conservando los
     * metadatos de paginacion (numero de pagina, tamano y total de elementos).
     *
     * @param pagina pagina de entidades devuelta por el repositorio
     * @return pagina de DTOs lista para serializar
     */
    public static Page<InsumoResponse> toResponsePage(Page<Insumo> pagina) {
        return pagina.map(InsumoMapper::toResponse);
    }
}
