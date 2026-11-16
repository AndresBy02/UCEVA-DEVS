package co.edu.uceva.presupuestoservice.repository;

import co.edu.uceva.presupuestoservice.model.ItemPresupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ItemPresupuestoRepository extends JpaRepository<ItemPresupuesto, UUID> {
}
