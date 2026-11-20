package co.edu.uceva.presupuestoservice.repository;

import co.edu.uceva.presupuestoservice.model.VersionPresupuesto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VersionPresupuestoRepository extends JpaRepository<VersionPresupuesto, UUID> {

    List<VersionPresupuesto> findByPresupuestoIdOrderByNumeroDesc(UUID presupuestoId);

    VersionPresupuesto findTopByPresupuestoIdOrderByNumeroDesc(UUID presupuestoId);

    long countByPresupuestoId(UUID presupuestoId);
}
