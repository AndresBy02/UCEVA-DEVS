package co.edu.uceva.insumoservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import co.edu.uceva.insumoservice.dto.HistorialPrecioResponse;
import co.edu.uceva.insumoservice.dto.InsumoResponse;
import co.edu.uceva.insumoservice.dto.InsumoResumenResponse;
import co.edu.uceva.insumoservice.model.HistorialPrecioInsumo;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.repository.HistorialPrecioRepository;
import co.edu.uceva.insumoservice.repository.InsumoRepository;

@Service
public class InsumoReadService {
    private final InsumoRepository insumoRepository;
    private final HistorialPrecioRepository historialRepository;

    public InsumoReadService(InsumoRepository insumoRepository,
            HistorialPrecioRepository historialRepository) {
        this.insumoRepository = insumoRepository;
        this.historialRepository = historialRepository;
    }

    @Transactional(readOnly = true)
    public List<InsumoResumenResponse> listar(Boolean activo) {
        List<Insumo> insumos = activo == null
                ? insumoRepository.findAll()
                : insumoRepository.findByActivo(activo);
        return insumos.stream().map(this::toSummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public InsumoResponse obtenerLocal(UUID id) {
        Insumo insumo = buscar(id);
        List<HistorialPrecioResponse> historial = historialRepository
                .findByInsumoIdOrderByFechaCambioDescIdDesc(id).stream()
                .map(this::toHistoryResponse)
                .toList();
        return new InsumoResponse(insumo.getId(), insumo.getCodigo(), insumo.getDescripcion(),
                insumo.getUnidadMedida(), insumo.getPrecioUnitario(), insumo.isActivo(),
                insumo.getUpdatedBy(), insumo.getUpdatedAt(), historial, List.of());
    }

    @Transactional(readOnly = true)
    public List<HistorialPrecioResponse> historial(UUID id) {
        buscar(id);
        return historialRepository.findByInsumoIdOrderByFechaCambioDescIdDesc(id).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private Insumo buscar(UUID id) {
        return insumoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Insumo no encontrado"));
    }

    private InsumoResumenResponse toSummaryResponse(Insumo insumo) {
        return new InsumoResumenResponse(insumo.getId(), insumo.getCodigo(), insumo.getDescripcion(),
                insumo.getUnidadMedida(), insumo.getPrecioUnitario(), insumo.isActivo(),
                insumo.getUpdatedBy(), insumo.getUpdatedAt());
    }

    private HistorialPrecioResponse toHistoryResponse(HistorialPrecioInsumo historial) {
        return new HistorialPrecioResponse(historial.getId(), historial.getPrecioAnterior(),
                historial.getPrecioNuevo(), historial.getModificadoPor(), historial.getFechaCambio());
    }
}
