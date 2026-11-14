package co.edu.uceva.insumoservice.repository;

import co.edu.uceva.insumoservice.model.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repositorio JPA del catalogo de insumos.
 *
 * <p>Ademas de las operaciones CRUD heredadas de {@link JpaRepository}, expone
 * la verificacion de unicidad del codigo usada por la capa de servicio al
 * crear un insumo.</p>
 */
@Repository
public interface InsumoRepository extends JpaRepository<Insumo, UUID> {

    /**
     * Indica si ya existe un insumo con el codigo indicado.
     *
     * @param codigo codigo unico del insumo
     * @return {@code true} si el codigo ya esta registrado
     */
    boolean existsByCodigo(String codigo);
}
