package co.edu.uceva.presupuestoservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.uceva.presupuestoservice.repository.PresupuestoUsoProjection;
import co.edu.uceva.presupuestoservice.repository.PresupuestoUsoRepository;

@RestController
@RequestMapping("/api/v1/presupuestos")
public class PresupuestoUsoController {
    private final PresupuestoUsoRepository presupuestoUsoRepository;

    public PresupuestoUsoController(PresupuestoUsoRepository presupuestoUsoRepository) {
        this.presupuestoUsoRepository = presupuestoUsoRepository;
    }

    @GetMapping
    public List<PresupuestoUsoProjection> listarPorInsumo(@RequestParam UUID insumoId) {
        return presupuestoUsoRepository.findByInsumoId(insumoId);
    }
}
