package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CapituloCreateRequest(
        @NotBlank(message = "El código del capítulo es obligatorio")
        @Size(max = 40)
        String codigo,

        @NotBlank(message = "El nombre del capítulo es obligatorio")
        @Size(max = 150)
        String nombre
) {
}
