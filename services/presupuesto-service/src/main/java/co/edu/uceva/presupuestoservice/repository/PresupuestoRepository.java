package co.edu.uceva.presupuestoservice.repository;

import co.edu.uceva.presupuestoservice.model.EstadoPresupuesto;
import co.edu.uceva.presupuestoservice.model.Presupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PresupuestoRepository extends JpaRepository<Presupuesto, UUID> {

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, UUID id);

    List<Presupuesto> findAllByOrderByFechaCreacionDesc();

    List<Presupuesto> findByEstadoOrderByFechaEnvioRevisionDesc(EstadoPresupuesto estado);

    @Query("select distinct p from Presupuesto p join p.capitulos c join c.items i "
            + "where i.insumoId = :insumoId order by p.fechaCreacion desc")
    List<Presupuesto> findDistinctByInsumoId(@Param("insumoId") UUID insumoId);
}
