package co.edu.uceva.presupuestoservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import co.edu.uceva.presupuestoservice.model.Presupuesto;

public interface PresupuestoUsoRepository extends Repository<Presupuesto, UUID> {
    @Query(value = """
            select distinct p.id as id, p.codigo as codigo, p.nombre as nombre, p.estado as estado
            from presupuestos p
            join capitulos c on c.presupuesto_id = p.id
            join items_presupuesto i on i.capitulo_id = c.id
            where i.insumo_id = :insumoId
            order by p.codigo
            """, nativeQuery = true)
    List<PresupuestoUsoProjection> findByInsumoId(@Param("insumoId") UUID insumoId);
}
