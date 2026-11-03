package org.example;

public class ErrorSemantico extends RuntimeException {
    public ErrorSemantico(String mensaje) {
        super(mensaje);
    }
}