package co.edu.uceva.insumoservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.uceva.insumoservice.model.Insumo;
import jakarta.persistence.LockModeType;

public interface InsumoRepository extends JpaRepository<Insumo, UUID> {
    List<Insumo> findByActivo(boolean activo);

    boolean existsByCodigoAndIdNot(String codigo, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Insumo i where i.id = :id")
    Optional<Insumo> findByIdForUpdate(@Param("id") UUID id);
}
