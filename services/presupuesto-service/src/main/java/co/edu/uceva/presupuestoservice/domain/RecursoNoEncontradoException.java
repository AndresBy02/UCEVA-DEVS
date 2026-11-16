package co.edu.uceva.presupuestoservice.domain;

public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String message) {
        super(message);
    }
}
