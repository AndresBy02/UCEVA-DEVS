package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearVersionRequest(
        @NotBlank(message = "La etiqueta de la versión es obligatoria")
        @Size(max = 120) String etiqueta,
        @Size(max = 500) String comentario
) {
}
