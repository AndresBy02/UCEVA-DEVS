package co.edu.uceva.insumoservice.client;

import java.util.List;
import java.util.UUID;

import co.edu.uceva.insumoservice.dto.PresupuestoUsoResponse;

public interface PresupuestoClient {
    List<PresupuestoUsoResponse> findByInsumo(UUID insumoId, String accessToken);
}
