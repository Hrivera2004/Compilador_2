package org.example.errores;

public class ErrorCompilacion extends RuntimeException {
    private final int linea;
    private final int columna;

    public ErrorCompilacion(String mensaje) {
        this(mensaje, -1, -1);
    }

    public ErrorCompilacion(String mensaje, int linea, int columna) {
        super(mensaje);
        this.linea = linea;
        this.columna = columna;
    }

    /** Línea del error (base 1), o -1 si no se conoce. */
    public int getLinea() {
        return linea;
    }

    /** Columna del error (base 1), o -1 si no se conoce. */
    public int getColumna() {
        return columna;
    }
}
