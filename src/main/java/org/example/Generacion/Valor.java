package org.example.Generacion;

public record Valor(String ref, String tipo) {
    @Override
    public String toString() {
        return tipo + " " + ref;
    }
}
