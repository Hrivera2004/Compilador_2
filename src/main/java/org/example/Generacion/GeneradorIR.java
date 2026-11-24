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
    private record Param(String nombre, String tipo) {}


    //buffers para cargar el flujo
    private final StringBuilder declaraciones = new StringBuilder(); // declaraciones
    private final StringBuilder strings = new StringBuilder(); // constantes de string

    private final StringBuilder globales = new StringBuilder(); // variables globales
    private final Map<String, Valor> varsGlobales = new HashMap<>();
    private final Map<String, Valor> varsLocales  = new HashMap<>();

    private final StringBuilder funciones = new StringBuilder(); // define de cada función

    //variables del estado de las funciones
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
    public Valor visitExpression(MicroCParser.ExpressionContext ctx) {
        if (ctx.equality_expression().size() == 1) {
            return visit(ctx.equality_expression(0));
        }
        Valor var = buscarVariable(nombreDestino(ctx.equality_expression(0)));
        Valor v   = visit(ctx.equality_expression(1));
        v = convertir(v, var.tipo());
        emitir("store " + v.tipo() + " " + v.ref() + ", ptr " + var.ref());
        return v;
    }

    @Override
    public Valor visitSimple_expression(MicroCParser.Simple_expressionContext ctx) {
        if (ctx.add_op() == null) return visit(ctx.term());

        Valor izq = visit(ctx.simple_expression());
        Valor der = visit(ctx.term());

        String op = ctx.add_op().getText().equals("+") ? "add" : "sub";
        return operar(op, izq, der);
    }

    @Override
    public Valor visitRelational_expression(
            MicroCParser.Relational_expressionContext ctx) {

        if (ctx.rel_op() == null) {
            return visit(ctx.simple_expression(0));
        }

        Valor izq = visit(ctx.simple_expression(0));
        Valor der = visit(ctx.simple_expression(1));

        String op = switch (ctx.rel_op().getText()) {
            case "<"  -> "slt";
            case "<=" -> "sle";
            case ">"  -> "sgt";
            case ">=" -> "sge";
            default -> throw new IllegalStateException(
                    "Operador relacional desconocido"
            );
        };

        return comparar(op, izq, der);
    }

    @Override
    public Valor visitEquality_expression(
            MicroCParser.Equality_expressionContext ctx) {

        if (ctx.eq_op() == null) {
            return visit(ctx.relational_expression(0));
        }

        Valor izq = visit(ctx.relational_expression(0));
        Valor der = visit(ctx.relational_expression(1));

        String op = switch (ctx.eq_op().getText()) {
            case "==" -> "eq";
            case "!=" -> "ne";
            default -> throw new IllegalStateException(
                    "Operador de igualdad desconocido"
            );
        };

        return comparar(op, izq, der);
    }

    @Override
    public Valor visitTerm(MicroCParser.TermContext ctx) {
        if (ctx.mul_op() == null) return visit(ctx.factor());

        Valor izq = visit(ctx.term());
        Valor der = visit(ctx.factor());

        String op = ctx.mul_op().getText().equals("*") ? "mul" : "sdiv";
        return operar(op, izq, der);
    }


    @Override
    public Valor visitFactor(MicroCParser.FactorContext ctx) {
        if (ctx.constant() != null) {
            return visit(ctx.constant());
        }

        if(ctx.identifier() != null && ctx.getChildCount()==1){
            Valor v = buscarVariable(ctx.identifier().getText());
            String t = nuevoTemp();
            emitir(t + " = load " + v.tipo() + ", ptr " + v.ref());
            return new Valor(t, v.tipo());
        }

        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }

        if (ctx.add_op() != null) {
            Valor v = visit(ctx.factor());
            if (ctx.add_op().getText().equals("+")){
                return v;
            }
            if (esConstanteLit(v)){
                short neg = (short) (-Integer.parseInt(v.ref()));
                return new Valor(String.valueOf(neg), "i16");
            }
            return operar("sub", new Valor("0", "i16"), v);
        }

        return visitChildren(ctx);
    }

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
        String texto = ctx.getText();
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
            throw new UnsupportedOperationException("Constantes string");
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



    @Override
    public Valor visitFunction_definition(MicroCParser.Function_definitionContext ctx){
        varsLocales.clear();
        dentroDeFuncion = true;
        var header = ctx.function_def_header();
        String nombre = header.identifier().getText();
        //main siempre es i32 : sino se retorna el tipo del header
        tipoRetorno = (nombre.equals("main")) ? "i32":tipoRetornoLLVM(header.return_type());

        contEtiqueta = 0;
        contTemp = 0;

        List<Param> params = new ArrayList<>();
        if(header.parameters_def().parameter_def_list() !=null){
            juntarParametros(header.parameters_def().parameter_def_list(), params);
        }

        List<String> firma = new ArrayList<>();
        for(Param p: params){
            firma.add(p.tipo + " %" + p.nombre + ".arg");
        }

        funciones.append("define ").append(tipoRetorno).append(" @").append(nombre)
                .append('(').append(String.join(", ", firma)).append(") {\n");

        funciones.append("entry:\n");
        terminado = false;

        for(Param p: params){
            String ptr = "%" + p.nombre +".addr";
            emitir(ptr +" = alloca " + p.tipo());
            emitir("store " + p.tipo() + " %" + p.nombre() + ".arg, ptr " + ptr);
            varsLocales.put(p.nombre(), new Valor(ptr, p.tipo()));
        }
        visit(ctx.function_body());

        if (!terminado) {
            retornoPorDefecto();
        }

        funciones.append("}\n\n");
        dentroDeFuncion = false;
        return null;

    }

    //helpers
    private String nombreDestino(MicroCParser.Equality_expressionContext eq) {
        MicroCParser.FactorContext f = eq.relational_expression(0)
                .simple_expression(0)
                .term()
                .factor();
        if (f.identifier() != null && f.getChildCount() == 1) {
            return f.identifier().getText();
        }
        throw new IllegalStateException("El lado izquierdo = no es una variable");
    }

    private Valor operar(String op, Valor izq, Valor der) {
        izq = convertir(izq, "i16");
        der = convertir(der, "i16");
        String t = nuevoTemp();
        emitir(t + " = " + op + " i16 " + izq.ref() + ", " + der.ref());
        return new Valor(t, "i16");
    }

    private Valor buscarVariable(String nombre){
        Valor v = varsLocales.get(nombre);
        if (v == null)
            v = varsGlobales.get(nombre);
        if (v == null)
            throw new IllegalStateException("Variable no declarada: " + nombre);
        return v;
    }

    private void juntarIdentificadores(MicroCParser.Identifier_listContext lista, List<String> out){
        if (lista.identifier_list() !=null){
            juntarIdentificadores(lista.identifier_list(), out);
        }
        out.add(lista.identifier().getText());
    }
    private void juntarParametros(MicroCParser.Parameter_def_listContext lista, List<Param> out) {
        if (lista.parameter_def_list() != null) {
            juntarParametros(lista.parameter_def_list(), out);
        }
        out.add(new Param(lista.identifier().getText(), tipoLLVM(lista.type())));
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
    private boolean esConstanteLit(Valor v) {
        return !v.ref().startsWith("%") && !v.ref().startsWith("@");
    }

    private Valor comparar(String op, Valor izq, Valor der) {
        izq = convertir(izq, "i16");
        der = convertir(der, "i16");
        String t = nuevoTemp();
        emitir(t + " = icmp " + op + " i16 " + izq.ref() + ", " + der.ref());
        return new Valor(t, "i1");
    }

    private Valor comoCondicion(Valor valor) {
        if (valor.tipo().equals("i1")) {
            return valor;
        }

        String t = nuevoTemp();

        emitir(t + " = icmp ne " + valor.tipo()
                + " " + valor.ref() + ", 0");

        return new Valor(t, "i1");
    }
}
