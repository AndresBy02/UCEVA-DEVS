package co.edu.uceva.insumoservice.repository;

import co.edu.uceva.insumoservice.model.HistorialPrecioInsumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HistorialPrecioInsumoRepository extends JpaRepository<HistorialPrecioInsumo, UUID> {

    List<HistorialPrecioInsumo> findByInsumoIdOrderByFechaCambioDesc(UUID insumoId);
}
