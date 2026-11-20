package co.edu.uceva.presupuestoservice.repository;

import co.edu.uceva.presupuestoservice.model.DesembolsoCapitulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DesembolsoCapituloRepository extends JpaRepository<DesembolsoCapitulo, UUID> {

    List<DesembolsoCapitulo> findByPresupuestoIdOrderBySemanaAsc(UUID presupuestoId);

    void deleteByPresupuestoId(UUID presupuestoId);

    void deleteByCapituloId(UUID capituloId);
}
