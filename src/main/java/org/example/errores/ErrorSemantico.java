package org.example.errores;

public class ErrorSemantico extends RuntimeException {
    public ErrorSemantico(String mensaje) {
        super(mensaje);
    }
}