package co.edu.uceva.insumoservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.uceva.insumoservice.model.HistorialPrecioInsumo;

public interface HistorialPrecioRepository extends JpaRepository<HistorialPrecioInsumo, UUID> {
    List<HistorialPrecioInsumo> findByInsumoIdOrderByFechaCambioDescIdDesc(UUID insumoId);

    HistorialPrecioInsumo findFirstByInsumoIdOrderByFechaCambioDescIdDesc(UUID insumoId);
}
