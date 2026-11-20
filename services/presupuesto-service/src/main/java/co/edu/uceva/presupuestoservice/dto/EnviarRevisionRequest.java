package co.edu.uceva.presupuestoservice.dto;

/** Datos opcionales del envío a revisión (RF-10 / UC-38). */
public record EnviarRevisionRequest(String destinatarioSub, String mensaje) {
}
