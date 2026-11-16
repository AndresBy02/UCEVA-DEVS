package co.edu.uceva.presupuestoservice.domain;

public class CodigoDuplicadoException extends RuntimeException {
    public CodigoDuplicadoException(String codigo) {
        super("Ya existe un presupuesto con el código: " + codigo);
    }
}
