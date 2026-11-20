package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.constraints.Size;

public record EnviarRevisionRequest(
        @Size(max = 120) String etiqueta,
        @Size(max = 500) String comentario
) {
}
