package co.edu.uceva.presupuestoservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DuplicarEscenarioRequest(
        @NotBlank(message = "El código del nuevo escenario es obligatorio")
        @Size(max = 40)
        String codigo,

        @NotBlank(message = "El nombre del nuevo escenario es obligatorio")
        @Size(max = 200)
        String nombre,

        @Size(max = 150)
        String cliente
) {
}
