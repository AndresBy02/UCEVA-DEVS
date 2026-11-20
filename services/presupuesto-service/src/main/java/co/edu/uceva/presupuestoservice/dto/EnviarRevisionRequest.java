package co.edu.uceva.presupuestoservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/**
 * Datos opcionales del envío a revisión (RF-10 / UC-38).
 * {@code mensaje} se acepta como alias de {@code comentario} para conservar
 * el contrato original de UC-38 sin duplicar el campo en el modelo.
 */
public record EnviarRevisionRequest(
        @Size(max = 120) String etiqueta,
        @Size(max = 500) @JsonAlias("mensaje") String comentario,
        @Size(max = 100) String destinatarioSub
) {
}
