package org.example.errores;

public class ErrorCompilacion extends RuntimeException {
    public ErrorCompilacion(String message) {
        super(message);
    }
}
