package co.edu.uceva.calculoservice.dto;

public record SpikeJnaResponse(
        boolean bibliotecaCargada,
        String nombreBiblioteca,
        double operandoA,
        double operandoB,
        double resultadoNativoC,
        String arquitecturaContenedor,
        String mensaje
) {}
