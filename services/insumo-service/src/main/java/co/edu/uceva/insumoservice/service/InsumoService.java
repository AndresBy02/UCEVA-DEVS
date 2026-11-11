package co.edu.uceva.insumoservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import co.edu.uceva.insumoservice.client.PresupuestoClient;
import co.edu.uceva.insumoservice.dto.HistorialPrecioResponse;
import co.edu.uceva.insumoservice.dto.InsumoRequest;
import co.edu.uceva.insumoservice.dto.InsumoResponse;
import co.edu.uceva.insumoservice.dto.InsumoResumenResponse;
import co.edu.uceva.insumoservice.dto.PresupuestoUsoResponse;

@Service
public class InsumoService {
    private final InsumoReadService readService;
    private final InsumoCommandService commandService;
    private final PresupuestoClient presupuestoClient;

    public InsumoService(InsumoReadService readService, InsumoCommandService commandService,
            PresupuestoClient presupuestoClient) {
        this.readService = readService;
        this.commandService = commandService;
        this.presupuestoClient = presupuestoClient;
    }

    public List<InsumoResumenResponse> listar(Boolean activo) {
        return readService.listar(activo);
    }

    public InsumoResponse obtener(UUID id, String accessToken) {
        InsumoResponse detalle = readService.obtenerLocal(id);
        List<PresupuestoUsoResponse> presupuestos = presupuestoClient.findByInsumo(id, accessToken);
        return new InsumoResponse(detalle.id(), detalle.codigo(), detalle.descripcion(),
                detalle.unidadMedida(), detalle.precioUnitario(), detalle.activo(),
                detalle.updatedBy(), detalle.updatedAt(), detalle.historialPrecios(), presupuestos);
    }

    public List<HistorialPrecioResponse> historial(UUID id) {
        return readService.historial(id);
    }

    public InsumoResponse actualizar(UUID id, InsumoRequest request, String modificadoPor, String accessToken) {
        commandService.actualizar(id, request, modificadoPor);
        return obtener(id, accessToken);
    }
}
