package org.example.Generacion;

import org.antlr.v4.runtime.tree.ParseTree;
import org.example.parser.MicroCBaseVisitor;
import org.example.parser.MicroCParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeneradorIR extends MicroCBaseVisitor<String> {
    //Data type
    private record Valor (String ref, String tipo) {
        @Override
        public String toString() {
            return tipo + " " + ref;
        }
    }


    //buffers para cargar el flujo
    private final StringBuilder declaraciones = new StringBuilder(); // declaraciones
    private final StringBuilder strings = new StringBuilder(); // constantes de string

    private final StringBuilder globales = new StringBuilder(); // variables globales
    private final Map<String, Valor> varsGlobales = new HashMap<>();
    private final Map<String, Valor> varsLocales  = new HashMap<>();

    private final StringBuilder funciones = new StringBuilder(); // define de cada función

    //cariables del estado de las funciones
    private int contTemp;
    private int contEtiqueta;
    private boolean terminado;
    private String tipoRetorno;

    private boolean dentroDeFuncion;

    //Metodo principal
    public String generar(ParseTree arbol) {
        visit(arbol);

        StringBuilder modulo = new StringBuilder("; Micro-C\n\n");
        for (StringBuilder seccion : List.of(declaraciones, strings, globales)) {
            if (!seccion.isEmpty()) {
                modulo.append(seccion).append('\n');
            }
        }
        modulo.append(funciones);
        return modulo.toString();
    }
    //variables
    @Override
    public String visitConstant(MicroCParser.ConstantContext ctx) {
        String texto = ctx.getText();   // "5", "0xFF", "'a'", or "\"hola\""
        String ref;
        String type;

        if (ctx.numeric_constant() != null) {
            int valor;
            if (texto.startsWith("0x") || texto.startsWith("0X")) {
                valor = Integer.parseInt(texto.substring(2), 16);
            } else {
                valor = Integer.parseInt(texto);
            }
            ref  = String.valueOf((short) valor);
            type = "i16";

        } else if (ctx.char_constant() != null) {
            char c = texto.charAt(1);
            ref  = String.valueOf((int) c);
            type = "i8";

        } else {
            // String: goes in the strings pool (Phase 5)
            throw new UnsupportedOperationException("String constants: Phase 5");
        }

        return new Valor(ref, type).toString();
    }
    @Override
    public String visitVariable_declaration(MicroCParser.Variable_declarationContext ctx){
        String type = ctx.type().getText();
        List<String> identifiers = new ArrayList<>();

        juntarIdentificadores(ctx.identifier_list(), identifiers);

        String tipo = type.equals("char") ? "i8" : "i16";
        for (String nombre : identifiers){
            if(dentroDeFuncion){
                String ptr = "%" + nombre + ".addr";
                emitir(ptr + " = alloca " + tipo);
                Valor act = new Valor(ptr,tipo);
                varsLocales.put(nombre, act);
            } else {
                globales.append("@" + nombre + " = global" + tipo  + " 0\n");
                Valor act = new Valor("@"+nombre,tipo);
                varsGlobales.put(nombre, act);
            }
        }
        return null;
    }

    private void juntarIdentificadores(MicroCParser.Identifier_listContext lista, List<String> out){
        if (lista.identifier_list() !=null){
            juntarIdentificadores(lista.identifier_list(), out);
        }
        out.add(lista.identifier().getText());
    }


    //Funciones
    @Override
    public String  visitFunction_definition(MicroCParser.Function_definitionContext ctx){
        dentroDeFuncion = true;
        var header = ctx.function_def_header();
        String nombre = header.identifier().getText();
        //main siempre es i32 : sino se retorna el tipo del header
        tipoRetorno = (nombre.equals("main")) ? "i32":tipoRetornoLLVM(header.return_type());

        contEtiqueta = 0;
        contTemp = 0;

        List<String> params = new ArrayList<>();
        if(header.parameters_def().parameter_def_list() !=null){
            juntarParametros(header.parameters_def().parameter_def_list(), params);
        }

        funciones.append("define ").append(tipoRetorno).append(" @").append(nombre)
                .append('(').append(String.join(", ", params)).append(") {\n");
        funciones.append("entry:\n");
        terminado = false;

        visit(ctx.function_body());

        if (!terminado) {
            retornoPorDefecto();
        }

        funciones.append("}\n\n");
        dentroDeFuncion = false;
        return null;

    }
    private void juntarParametros(MicroCParser.Parameter_def_listContext lista, List<String> out) {
        if (lista.parameter_def_list() != null) {
            juntarParametros(lista.parameter_def_list(), out);
        }
        out.add(tipoLLVM(lista.type()) + " %" + lista.identifier().getText());
    }

    private void retornoPorDefecto() {
        if (tipoRetorno.equals("void")) {
            terminar("ret void");
        } else {
            terminar("ret " + tipoRetorno + " 0");
        }
    }
    //helpers

    private void emitir(String inst){
        if (terminado)
            abrirBloque(nuevaEtiqueta("muerto"));
        funciones.append(" ").append(inst).append("\n");

    }

    private void terminar(String instruccion) {
        emitir(instruccion);
        terminado = true;
    }

    private void abrirBloque(String etq){
        if(!terminado)
            funciones.append("br label %").append(etq).append("\n");
        funciones.append(etq).append(":\n");
        terminado = false;
    }
    private String nuevoTemp(){
        return "%t" + contTemp++;
    }
    private String nuevaEtiqueta(String base){
        return base + contEtiqueta++;
    }

    static String tipoLLVM(MicroCParser.TypeContext tipo){
        return tipo.getText().equals("char") ? "i8" : "i16";
    }
    static String tipoRetornoLLVM(MicroCParser.Return_typeContext  ret){
        return ret.type() == null ? "void" : tipoLLVM(ret.type());
    }

}
