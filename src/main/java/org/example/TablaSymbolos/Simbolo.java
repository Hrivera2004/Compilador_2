package org.example.TablaSymbolos;

import org.antlr.v4.runtime.Token;
import org.example.enums.ClaseSimbolo;
import org.example.enums.Tipo;

import java.util.List;

public record Simbolo(
        String nombre,
        Tipo tipo,
        ClaseSimbolo clase,
        List<Tipo> tiposParametros,
        Token declaracion,
        boolean variadica
) {
    public Simbolo {
        tiposParametros = List.copyOf(tiposParametros);
    }
    public boolean esFuncion() {
        return clase == ClaseSimbolo.Funcion;
    }
}
