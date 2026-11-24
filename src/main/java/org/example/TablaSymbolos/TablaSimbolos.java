package org.example.TablaSymbolos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public class TablaSimbolos {
    private final Deque<Map<String, Simbolo>> ambitos = new ArrayDeque<>();
    private final Map<String, Simbolo> funciones = new LinkedHashMap<>();

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
        if (simbolo.esFuncion()) {
            funciones.putIfAbsent(simbolo.nombre(), simbolo);
        }
        return true;
    }

    public Map<String, Simbolo> funciones() {
        return Collections.unmodifiableMap(funciones);
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
