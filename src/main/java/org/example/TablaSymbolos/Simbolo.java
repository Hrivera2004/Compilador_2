package org.example.TablaSymbolos;

import org.antlr.v4.runtime.Token;
import java.util.List;

public record Simbolo(
        String nombre,
        Tipo tipo,
        ClaseSimbolo clase,
        List<Tipo> tiposParametros,
        Token declaracion
) {
    public Simbolo {
        tiposParametros = List.copyOf(tiposParametros);
    }
    public boolean esFuncion() {
        return clase == ClaseSimbolo.Funcion;
    }
}
