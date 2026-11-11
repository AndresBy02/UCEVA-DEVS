package co.edu.uceva.insumoservice.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import co.edu.uceva.insumoservice.dto.InsumoRequest;
import co.edu.uceva.insumoservice.model.HistorialPrecioInsumo;
import co.edu.uceva.insumoservice.model.Insumo;
import co.edu.uceva.insumoservice.repository.HistorialPrecioRepository;
import co.edu.uceva.insumoservice.repository.InsumoRepository;

@Service
public class InsumoCommandService {
    private final InsumoRepository insumoRepository;
    private final HistorialPrecioRepository historialRepository;

    public InsumoCommandService(InsumoRepository insumoRepository,
            HistorialPrecioRepository historialRepository) {
        this.insumoRepository = insumoRepository;
        this.historialRepository = historialRepository;
    }

    @Transactional
    public void actualizar(UUID id, InsumoRequest request, String modificadoPor) {
        validarPrecio(request);
        Insumo insumo = insumoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Insumo no encontrado"));
        var precioAnterior = insumo.getPrecioUnitario();
        var precioNuevo = request.precioUnitario();
        var actualizadoEn = OffsetDateTime.now(ZoneOffset.UTC);

        insumo.setDescripcion(request.descripcion());
        insumo.setUnidadMedida(request.unidadMedida());
        insumo.setPrecioUnitario(precioNuevo);
        insumo.setActivo(request.activo());
        insumo.setUpdatedBy(modificadoPor);
        insumo.setUpdatedAt(actualizadoEn);
        insumoRepository.save(insumo);

        if (precioAnterior.compareTo(precioNuevo) != 0) {
            historialRepository.save(new HistorialPrecioInsumo(
                    UUID.randomUUID(), id, precioAnterior, precioNuevo, modificadoPor,
                    siguienteFechaCambio(id, actualizadoEn)));
        }
    }

    private OffsetDateTime siguienteFechaCambio(UUID insumoId, OffsetDateTime ahora) {
        HistorialPrecioInsumo ultimoCambio = historialRepository
                .findFirstByInsumoIdOrderByFechaCambioDescIdDesc(insumoId);
        if (ultimoCambio == null) {
            return ahora;
        }
        OffsetDateTime siguienteInstante = ultimoCambio.getFechaCambio().plusNanos(1_000);
        return ahora.isAfter(siguienteInstante) ? ahora : siguienteInstante;
    }

    private void validarPrecio(InsumoRequest request) {
        if (request.precioUnitario() == null
                || request.precioUnitario().signum() < 0
                || request.precioUnitario().scale() > 3
                || request.precioUnitario().precision() - request.precioUnitario().scale() > 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El precio unitario debe ser no negativo, admite hasta 16 dígitos enteros y máximo 3 decimales");
        }
    }
}
