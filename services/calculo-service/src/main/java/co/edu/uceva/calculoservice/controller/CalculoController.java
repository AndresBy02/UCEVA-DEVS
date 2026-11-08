package co.edu.uceva.calculoservice.controller;

import co.edu.uceva.calculoservice.dto.CalculoPresupuestoRequest;
import co.edu.uceva.calculoservice.dto.CalculoPresupuestoResponse;
import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraRequest;
import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraResponse;
import co.edu.uceva.calculoservice.dto.SpikeJnaResponse;
import co.edu.uceva.calculoservice.service.FinanzasEvaluacionService;
import co.edu.uceva.calculoservice.service.PresupuestoCalculoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calculos")
public class CalculoController {

    private final PresupuestoCalculoService presupuestoCalculoService;
    private final FinanzasEvaluacionService finanzasEvaluacionService;

    public CalculoController(
            PresupuestoCalculoService presupuestoCalculoService,
            FinanzasEvaluacionService finanzasEvaluacionService
    ) {
        this.presupuestoCalculoService = presupuestoCalculoService;
        this.finanzasEvaluacionService = finanzasEvaluacionService;
    }

    @GetMapping("/spike-jna")
    public ResponseEntity<SpikeJnaResponse> spikeJna(
            @RequestParam(defaultValue = "25.5") double a,
            @RequestParam(defaultValue = "14.5") double b
    ) {
        return ResponseEntity.ok(finanzasEvaluacionService.ejecutarSpikeJna(a, b));
    }

    @PostMapping("/presupuesto")
    public ResponseEntity<CalculoPresupuestoResponse> calcularPresupuesto(
            @RequestBody CalculoPresupuestoRequest request
    ) {
        return ResponseEntity.ok(presupuestoCalculoService.calcularPresupuesto(request));
    }

    @PostMapping("/evaluacion-financiera")
    public ResponseEntity<EvaluacionFinancieraResponse> evaluarProyecto(
            @RequestBody EvaluacionFinancieraRequest request
    ) {
        return ResponseEntity.ok(finanzasEvaluacionService.evaluarProyecto(request));
    }
}
