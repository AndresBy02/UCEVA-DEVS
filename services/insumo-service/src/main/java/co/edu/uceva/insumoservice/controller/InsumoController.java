package co.edu.uceva.insumoservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.uceva.insumoservice.dto.InsumoRequest;
import co.edu.uceva.insumoservice.dto.HistorialPrecioResponse;
import co.edu.uceva.insumoservice.dto.InsumoResponse;
import co.edu.uceva.insumoservice.dto.InsumoResumenResponse;
import co.edu.uceva.insumoservice.service.InsumoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/insumos")
@Validated
public class InsumoController {
    private final InsumoService insumoService;

    public InsumoController(InsumoService insumoService) {
        this.insumoService = insumoService;
    }

    @GetMapping
    public List<InsumoResumenResponse> listar(@RequestParam(required = false) Boolean activo) {
        return insumoService.listar(activo);
    }

    @GetMapping("/{id}")
    public InsumoResponse obtener(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return insumoService.obtener(id, jwt.getTokenValue());
    }

    @GetMapping("/{id}/historial")
    public List<HistorialPrecioResponse> historial(@PathVariable UUID id) {
        return insumoService.historial(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InsumoResponse> actualizar(@PathVariable UUID id,
            @Valid @RequestBody InsumoRequest request, @AuthenticationPrincipal Jwt jwt) {
        String modificadoPor = identidadDeAuditoria(jwt);
        return ResponseEntity.ok(insumoService.actualizar(
                id, request, modificadoPor, jwt.getTokenValue()));
    }

    private String identidadDeAuditoria(Jwt jwt) {
        String subject = jwt.getSubject();
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername == null || preferredUsername.isBlank()) {
            return subject.length() <= 100 ? subject : subject.substring(0, 100);
        }

        String suffix = " (" + subject + ")";
        if (suffix.length() >= 100) {
            return suffix.substring(0, 100);
        }
        int usernameLength = Math.min(preferredUsername.length(), 100 - suffix.length());
        return preferredUsername.substring(0, usernameLength) + suffix;
    }
}
