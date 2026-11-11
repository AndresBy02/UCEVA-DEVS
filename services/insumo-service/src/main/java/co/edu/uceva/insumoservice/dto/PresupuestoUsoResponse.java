package co.edu.uceva.insumoservice.dto;

import java.util.UUID;

public record PresupuestoUsoResponse(UUID id, String codigo, String nombre, String estado) {
}
