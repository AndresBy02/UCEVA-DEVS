package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazarPresupuestoRequest(
        @NotBlank(message = "El motivo del rechazo es obligatorio")
        @Size(max = 1000, message = "El motivo del rechazo no puede superar 1000 caracteres")
        String motivo
) {
}
