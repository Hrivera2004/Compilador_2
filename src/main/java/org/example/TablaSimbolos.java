package org.example;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class TablaSimbolos {
    private final Deque<Map<String, Simbolo>> ambitos = new ArrayDeque<>();

    public TablaSimbolos(){
        entrarAmbito();
    }

    public void entrarAmbito(){
        ambitos.push(new HashMap<>());
    }

    public void salirAmbito() {
        if (ambitos.size() <= 1) {
            throw new IllegalStateException("No se puede cerrar el ámbito global");
        }
        ambitos.pop();
    }

    // false significa que ya existe en este mismo ámbito
    public boolean declarar(Simbolo simbolo) {
        Map<String, Simbolo> actual = ambitos.peek();

        if (actual.containsKey(simbolo.nombre())) {
            return false;
        }

        actual.put(simbolo.nombre(), simbolo);
        return true;
    }

    // Busca desde el ámbito más interno hacia el global
    public Simbolo resolver(String nombre) {
        for (Map<String, Simbolo> ambito : ambitos) {
            Simbolo simbolo = ambito.get(nombre);

            if (simbolo != null) {
                return simbolo;
            }
        }

        return null;
    }

}
