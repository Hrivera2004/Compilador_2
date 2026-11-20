package org.example.Generacion;

import org.antlr.v4.runtime.tree.ParseTree;
import org.example.parser.MicroCBaseVisitor;
import org.example.parser.MicroCParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeneradorIR extends MicroCBaseVisitor<Valor> {
    //Data type



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
    //visitors
    @Override
    public Valor visitStatement(MicroCParser.StatementContext ctx) {
        String primero = ctx.getChild(0).getText();

        if (primero.equals("return")) {
            if (ctx.expression() == null) {
                retornoPorDefecto();
            } else {
                Valor v = visit(ctx.expression());
                v = convertir(v, tipoRetorno);
                terminar("ret " + v.tipo() + " " + v.ref());
            }
            return null;
        }

        return visitChildren(ctx);
    }

    @Override
    public Valor visitConstant(MicroCParser.ConstantContext ctx) {
        String texto = ctx.getText();   // "5", "0xFF", "'a'", or "\"hola\""
        String ref;
        String type;

        if (ctx.numeric_constant() != null) {
            int valor;
            if (texto.toLowerCase().startsWith("0x")) {
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
            throw new UnsupportedOperationException("Constantes string: Fase 5");
        }

        return new Valor(ref, type);
    }
    @Override
    public Valor visitVariable_declaration(MicroCParser.Variable_declarationContext ctx){
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
                globales.append("@" + nombre + " = global " + tipo  + " 0\n");
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

    @Override
    public Valor  visitFunction_definition(MicroCParser.Function_definitionContext ctx){
        varsLocales.clear();
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

    //helpers
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
    private Valor convertir(Valor v, String destino) {
        if (v.tipo().equals(destino)) return v;

        String t = nuevoTemp();
        if (bits(v.tipo()) < bits(destino)) {
            String op = v.tipo().equals("i1") ? "zext" : "sext";
            emitir(t + " = " + op + " " + v.tipo() + " " + v.ref() + " to " + destino);
        } else {
            emitir(t + " = trunc " + v.tipo() + " " + v.ref() + " to " + destino);
        }
        return new Valor(t, destino);
    }

    private int bits(String tipo) {
        return Integer.parseInt(tipo.substring(1));   // "i16" -> 16
    }

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
