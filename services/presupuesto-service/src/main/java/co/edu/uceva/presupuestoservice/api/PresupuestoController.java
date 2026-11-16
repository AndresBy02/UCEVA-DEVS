package co.edu.uceva.presupuestoservice.api;

import co.edu.uceva.presupuestoservice.dto.CalculoDtos.InsumosCriticosResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.SimulacionSensibilidadResponse;
import co.edu.uceva.presupuestoservice.dto.CapituloCreateRequest;
import co.edu.uceva.presupuestoservice.dto.ComparacionEscenariosResponse;
import co.edu.uceva.presupuestoservice.dto.DuplicarEscenarioRequest;
import co.edu.uceva.presupuestoservice.dto.ItemCreateRequest;
import co.edu.uceva.presupuestoservice.dto.ItemUpdateRequest;
import co.edu.uceva.presupuestoservice.dto.PresupuestoDetalleResponse;
import co.edu.uceva.presupuestoservice.dto.PresupuestoRequest;
import co.edu.uceva.presupuestoservice.dto.PresupuestoResumenResponse;
import co.edu.uceva.presupuestoservice.dto.PresupuestoUpdateRequest;
import co.edu.uceva.presupuestoservice.dto.PrevisualizacionPreciosResponse;
import co.edu.uceva.presupuestoservice.dto.SensibilidadRequest;
import co.edu.uceva.presupuestoservice.service.PresupuestoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/presupuestos")
public class PresupuestoController {

    private final PresupuestoService presupuestoService;

    public PresupuestoController(PresupuestoService presupuestoService) {
        this.presupuestoService = presupuestoService;
    }

    @PostMapping
    public ResponseEntity<PresupuestoDetalleResponse> crear(
            @Valid @RequestBody PresupuestoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        PresupuestoDetalleResponse creado = presupuestoService.crearPresupuesto(request, extraerSub(jwt));
        return ResponseEntity.created(URI.create("/api/v1/presupuestos/" + creado.id())).body(creado);
    }

    @GetMapping
    public ResponseEntity<List<PresupuestoResumenResponse>> listar() {
        return ResponseEntity.ok(presupuestoService.listarPresupuestos());
    }

    @GetMapping("/por-insumo/{insumoId}")
    public ResponseEntity<List<PresupuestoResumenResponse>> listarPorInsumo(@PathVariable UUID insumoId) {
        return ResponseEntity.ok(presupuestoService.listarPorInsumo(insumoId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PresupuestoDetalleResponse> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(presupuestoService.obtenerDetalle(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PresupuestoDetalleResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody PresupuestoUpdateRequest request) {
        return ResponseEntity.ok(presupuestoService.actualizarPresupuesto(id, request));
    }

    @PostMapping("/{id}/capitulos")
    public ResponseEntity<PresupuestoDetalleResponse> agregarCapitulo(
            @PathVariable UUID id,
            @Valid @RequestBody CapituloCreateRequest request) {
        return ResponseEntity.ok(presupuestoService.agregarCapitulo(id, request));
    }

    @PutMapping("/{id}/capitulos/{capituloId}")
    public ResponseEntity<PresupuestoDetalleResponse> actualizarCapitulo(
            @PathVariable UUID id,
            @PathVariable UUID capituloId,
            @Valid @RequestBody CapituloCreateRequest request) {
        return ResponseEntity.ok(presupuestoService.actualizarCapitulo(id, capituloId, request));
    }

    @DeleteMapping("/{id}/capitulos/{capituloId}")
    public ResponseEntity<PresupuestoDetalleResponse> eliminarCapitulo(
            @PathVariable UUID id,
            @PathVariable UUID capituloId) {
        return ResponseEntity.ok(presupuestoService.eliminarCapitulo(id, capituloId));
    }

    @PostMapping("/{id}/capitulos/{capituloId}/items")
    public ResponseEntity<PresupuestoDetalleResponse> agregarItem(
            @PathVariable UUID id,
            @PathVariable UUID capituloId,
            @Valid @RequestBody ItemCreateRequest request) {
        return ResponseEntity.ok(presupuestoService.agregarItem(id, capituloId, request));
    }

    @PutMapping("/{id}/capitulos/{capituloId}/items/{itemId}")
    public ResponseEntity<PresupuestoDetalleResponse> actualizarItem(
            @PathVariable UUID id,
            @PathVariable UUID capituloId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemUpdateRequest request) {
        return ResponseEntity.ok(presupuestoService.actualizarItem(id, capituloId, itemId, request));
    }

    @DeleteMapping("/{id}/capitulos/{capituloId}/items/{itemId}")
    public ResponseEntity<PresupuestoDetalleResponse> eliminarItem(
            @PathVariable UUID id,
            @PathVariable UUID capituloId,
            @PathVariable UUID itemId) {
        return ResponseEntity.ok(presupuestoService.eliminarItem(id, capituloId, itemId));
    }

    @GetMapping("/{id}/actualizar-precios/preview")
    public ResponseEntity<PrevisualizacionPreciosResponse> previsualizarActualizacionPrecios(@PathVariable UUID id) {
        return ResponseEntity.ok(presupuestoService.previsualizarActualizacionPrecios(id));
    }

    @PostMapping({"/{id}/actualizar-precios", "/{id}/sincronizar-precios"})
    public ResponseEntity<PresupuestoDetalleResponse> aplicarActualizacionPrecios(@PathVariable UUID id) {
        return ResponseEntity.ok(presupuestoService.aplicarActualizacionPrecios(id));
    }

    @PostMapping("/{id}/duplicar")
    public ResponseEntity<PresupuestoDetalleResponse> duplicarComoEscenario(
            @PathVariable UUID id,
            @Valid @RequestBody DuplicarEscenarioRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        PresupuestoDetalleResponse copia = presupuestoService.duplicarComoEscenario(id, request, extraerSub(jwt));
        return ResponseEntity.created(URI.create("/api/v1/presupuestos/" + copia.id())).body(copia);
    }

    @GetMapping("/{idA}/comparar/{idB}")
    public ResponseEntity<ComparacionEscenariosResponse> compararEscenarios(
            @PathVariable UUID idA,
            @PathVariable UUID idB) {
        return ResponseEntity.ok(presupuestoService.compararEscenarios(idA, idB));
    }

    @PostMapping("/{id}/sensibilidad")
    public ResponseEntity<SimulacionSensibilidadResponse> simularSensibilidad(
            @PathVariable UUID id,
            @Valid @RequestBody SensibilidadRequest request) {
        return ResponseEntity.ok(
                presupuestoService.simularSensibilidad(id, request.codigoInsumo(), request.porcentajeVariacion()));
    }

    @GetMapping("/{id}/insumos-criticos")
    public ResponseEntity<InsumosCriticosResponse> obtenerInsumosCriticos(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "10.00") BigDecimal porcentaje) {
        return ResponseEntity.ok(presupuestoService.obtenerInsumosCriticos(id, porcentaje));
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<PresupuestoDetalleResponse> aprobar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(presupuestoService.aprobarPresupuesto(id, extraerSub(jwt)));
    }

    private static String extraerSub(Jwt jwt) {
        return jwt != null && jwt.getSubject() != null ? jwt.getSubject() : "anonimo";
    }
}
