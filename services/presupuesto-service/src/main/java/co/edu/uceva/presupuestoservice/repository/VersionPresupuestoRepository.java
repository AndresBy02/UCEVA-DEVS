package co.edu.uceva.presupuestoservice.repository;

import co.edu.uceva.presupuestoservice.model.VersionPresupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VersionPresupuestoRepository extends JpaRepository<VersionPresupuesto, UUID> {

    List<VersionPresupuesto> findByPresupuestoIdOrderByNumeroVersionDesc(UUID presupuestoId);

    Optional<VersionPresupuesto> findByIdAndPresupuestoId(UUID id, UUID presupuestoId);

    @Query("select coalesce(max(v.numeroVersion), 0) from VersionPresupuesto v where v.presupuestoId = :presupuestoId")
    int findMaxNumeroVersionByPresupuestoId(@Param("presupuestoId") UUID presupuestoId);

    long countByPresupuestoId(UUID presupuestoId);
}
