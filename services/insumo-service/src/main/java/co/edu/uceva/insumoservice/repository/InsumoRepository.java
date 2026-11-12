package co.edu.uceva.insumoservice.repository;

import co.edu.uceva.insumoservice.model.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repositorio JPA del catalogo de insumos.
 *
 * <p>Sin consultas adicionales por ahora: la capa de API se incorpora en la
 * siguiente historia (UC-18).</p>
 */
@Repository
public interface InsumoRepository extends JpaRepository<Insumo, UUID> {
}
