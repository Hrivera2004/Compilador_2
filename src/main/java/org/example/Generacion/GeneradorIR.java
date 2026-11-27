package org.example.Generacion;

import org.antlr.v4.runtime.tree.ParseTree;
import org.example.TablaSymbolos.Simbolo;
import org.example.TablaSymbolos.TablaSimbolos;
import org.example.parser.MicroCBaseVisitor;
import org.example.parser.MicroCParser;
import org.example.enums.Tipo;

import java.util.HashSet;
import java.util.Set;
import java.util.*;

public class GeneradorIR extends MicroCBaseVisitor<Valor> {
    //Data type
    private record Param(String nombre, String tipo) {}
    private final TablaSimbolos tabla;
    private final Set<String> funcionesDefinidas = new HashSet<>();

    public GeneradorIR(TablaSimbolos tabla) {
        this.tabla = tabla;
    }

    //buffers para cargar el flujo
    private final StringBuilder declaraciones = new StringBuilder(); // declaraciones
    private final StringBuilder strings = new StringBuilder(); // constantes de string

    private final Map<String, Valor> poolStrings = new LinkedHashMap<>();
    private int contadorStrings;

    private final StringBuilder globales = new StringBuilder(); // variables globales
    private final Map<String, Valor> varsGlobales = new HashMap<>();
    private final Map<String, Valor> varsLocales  = new HashMap<>();

    private final StringBuilder Bufferfunciones = new StringBuilder(); // define de cada función

    private final Deque<String> destinosBreak = new ArrayDeque<>();
    private final Deque<String> destinosContinue = new ArrayDeque<>();

    //variables del estado de las Bufferfunciones
    private int contTemp;
    private int contEtiqueta;
    private boolean terminado;
    private String tipoRetorno;

    private boolean dentroDeFuncion;

    //Metodo principal
    public String generar(ParseTree arbol) {
        visit(arbol);
        emitirDeclaresRuntime();
        emitirDeclaresExternos();

        StringBuilder modulo = new StringBuilder("; Micro-C\n\n");
        for (StringBuilder seccion : List.of(declaraciones, strings, globales)) {
            if (!seccion.isEmpty()) {
                modulo.append(seccion).append('\n');
            }
        }
        modulo.append(Bufferfunciones);
        return modulo.toString();
    }
    //visitors
    @Override
    public Valor visitFunction_declaration(MicroCParser.Function_declarationContext ctx) {
        return null;
    }

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
        if (ctx.identifier() != null) {
            return generarLlamada(ctx);
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

        if (primero.equals("if") && ctx.statement().size() == 1) {
            return generarIfSinElse(ctx);
        }

        if (primero.equals("if") && ctx.statement().size() == 2) {
            return generarIfConElse(ctx);
        }

        if (primero.equals("while")) {
            return generarWhile(ctx);
        }

        if (primero.equals("break")) {
            if (destinosBreak.isEmpty()) {
                throw new IllegalStateException(
                        "break fuera de un while"
                );
            }

            terminar("br label %" + destinosBreak.peek());
            return null;
        }

        if (primero.equals("continue")) {
            if (destinosContinue.isEmpty()) {
                throw new IllegalStateException(
                        "continue fuera de un while"
                );
            }

            terminar("br label %" + destinosContinue.peek());
            return null;
        }

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
            return registrarCadena(texto);
        }

        return new Valor(ref, type);
    }
    @Override
    public Valor visitVariable_declaration(MicroCParser.Variable_declarationContext ctx){
        String type = ctx.type().getText();
        List<String> identifiers = new ArrayList<>();

        juntarIdentificadores(ctx.identifier_list(), identifiers);

        String tipo = tipoLLVM(ctx.type());
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
        tipoRetorno = retornoLLVM(tabla.funciones().get(nombre));

        contEtiqueta = 0;
        contTemp = 0;

        List<Param> params = new ArrayList<>();
        if(header.parameters_def().parameter_def_list() !=null){
            juntarParametros(header.parameters_def().parameter_def_list(), params);
        }
        funcionesDefinidas.add(nombre);
        List<String> firma = new ArrayList<>();
        for(Param p: params){
            firma.add(p.tipo + " %" + p.nombre + ".arg");
        }

        Bufferfunciones.append("define ").append(tipoRetorno).append(" @").append(nombre)
                .append('(').append(String.join(", ", firma)).append(") {\n");

        Bufferfunciones.append("entry:\n");
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

        Bufferfunciones.append("}\n\n");
        dentroDeFuncion = false;
        return null;

    }

    //helpers
    private Valor registrarCadena(String literal) {
        Valor existente = poolStrings.get(literal);

        if (existente != null) {
            return existente;
        }

        StringBuilder bytesLLVM = new StringBuilder();
        int longitud = 0;

        for (int i = 1; i < literal.length() - 1; i++) {
            char caracter = literal.charAt(i);

            if (caracter == '\\') {
                char escape = literal.charAt(++i);

                switch (escape) {
                    case 'n' -> bytesLLVM.append("\\0A");
                    case '\\' -> bytesLLVM.append("\\5C");
                    case '"' -> bytesLLVM.append("\\22");
                    default -> throw new IllegalStateException(
                            "Escape de string no soportado: \\" + escape
                    );
                }

                longitud++;
                continue;
            }

            if (caracter >= 32 && caracter <= 126
                    && caracter != '"' && caracter != '\\') {
                bytesLLVM.append(caracter);
            } else {
                bytesLLVM.append(String.format(
                        "\\%02X", (int) caracter & 0xFF
                ));
            }

            longitud++;
        }

        String referencia = "@.str" + contadorStrings++;
        strings.append(referencia)
                .append(" = private unnamed_addr constant [")
                .append(longitud + 1)
                .append(" x i8] c\"")
                .append(bytesLLVM)
                .append("\\00\"\n");

        Valor valor = new Valor(referencia, "ptr");
        poolStrings.put(literal, valor);

        return valor;
    }

    private Valor generarLlamada(MicroCParser.FactorContext ctx) {
        String nombre = ctx.identifier().getText();
        if (nombre.equals("printf")) {
            return generarPrintf(ctx);
        }

        if (nombre.equals("scanf")) {
            return generarScanf(ctx);
        }

        Simbolo f = tabla.funciones().get(nombre);
        if (f == null) {
            throw new IllegalStateException("Función sin firma en la tabla: " + nombre);
        }
        String retorno = retornoLLVM(f);
        List<String> params = paramsLLVM(f);

        List<MicroCParser.ExpressionContext> exprs = new ArrayList<>();
        juntarArgumentos(ctx.expression_list(), exprs);

        // Se evalúan en orden; cada argumento se ajusta al tipo del parámetro
        // (p. ej. una comparación i1 se extiende a i16)
        List<String> args = new ArrayList<>();
        for (int i = 0; i < exprs.size(); i++) {
            Valor v = visit(exprs.get(i));
            String destino = (i < params.size()) ? params.get(i) : promocionVariadica(v);
            v = convertir(v, destino);
            args.add(v.tipo() + " " + v.ref());
        }


        String tipoCall = retorno;
        if (f.variadica()) {
            tipoCall += " (" + String.join(", ", params) + ", ...)";
        }
        String llamada = "call " + tipoCall + " @" + nombre + "(" + String.join(", ", args) + ")";

        if (retorno.equals("void")) {
            emitir(llamada);
            return null;
        }
        String t = nuevoTemp();
        emitir(t + " = " + llamada);
        return new Valor(t, retorno);
    }

    private Valor generarPrintf(MicroCParser.FactorContext ctx) {
        List<MicroCParser.ExpressionContext> argumentos = new ArrayList<>();
        juntarArgumentos(ctx.expression_list(), argumentos);

        Valor formato = visit(argumentos.getFirst());

        if (!formato.tipo().equals("ptr")) {
            throw new IllegalStateException(
                    "printf esperaba un formato de tipo string"
            );
        }

        if (argumentos.size() == 1) {
            emitir("call void @microc_printf_text(ptr " + formato.ref() + ")");
            return null;
        }

        Valor valor = visit(argumentos.get(1));

        // ayuda a que i1 pueda caer dentro del switch
        if (valor.tipo().equals("i1")) {
            valor = convertir(valor, "i16");
        }

        switch (valor.tipo()) {
            case "i16" -> emitir(
                    "call void @microc_printf_i16(ptr " + formato.ref()
                            + ", i16 " + valor.ref() + ")"
            );
            case "i8" -> emitir(
                    "call void @microc_printf_i8(ptr " + formato.ref()
                            + ", i8 " + valor.ref() + ")"
            );
            default -> throw new IllegalStateException(
                    "printf recibió un tipo no imprimible: " + valor.tipo()
            );
        }

        return null;
    }


    private Valor generarScanf(MicroCParser.FactorContext ctx) {
        List<MicroCParser.ExpressionContext> argumentos = new ArrayList<>();
        juntarArgumentos(ctx.expression_list(), argumentos);

        Valor formato = visit(argumentos.getFirst());

        if (!formato.tipo().equals("ptr")) {
            throw new IllegalStateException(
                    "scanf esperaba un formato de tipo string"
            );
        }

        String textoFormato = argumentos.getFirst().getText();
        String temporal = nuevoTemp();

        if (textoFormato.equals("\"%d\"")) {
            emitir(temporal + " = call i16 @microc_scan_i16(ptr "
                    + formato.ref() + ")");
            return new Valor(temporal, "i16");
        }

        if (textoFormato.equals("\"%c\"")) {
            emitir(temporal + " = call i8 @microc_scan_i8(ptr "
                    + formato.ref() + ")");
            return new Valor(temporal, "i8");
        }

        throw new IllegalStateException(
                "Formato de scanf no soportado: " + textoFormato
        );
    }

    private void juntarArgumentos(MicroCParser.Expression_listContext lista, List<MicroCParser.ExpressionContext> out) {
        if (lista == null) {
            return;
        }
        juntarArgumentos(lista.expression_list(), out);
        out.add(lista.expression());
    }
    private static boolean esPredefinida(String nombre) {
        return nombre.equals("printf") || nombre.equals("scanf");
    }

    private void emitirDeclaresRuntime() {
        declaraciones.append("declare void @microc_printf_text(ptr)\n");
        declaraciones.append("declare void @microc_printf_i16(ptr, i16)\n");
        declaraciones.append("declare void @microc_printf_i8(ptr, i8)\n");
        declaraciones.append("declare i16 @microc_scan_i16(ptr)\n");
        declaraciones.append("declare i8 @microc_scan_i8(ptr)\n");
    }

    private void emitirDeclaresExternos() {
        for (Simbolo f : tabla.funciones().values()) {
            if (esPredefinida(f.nombre()) || funcionesDefinidas.contains(f.nombre())) {
                continue;
            }
            List<String> params = paramsLLVM(f);
            if (f.variadica()) {
                params.add("...");
            }
            declaraciones.append("declare ").append(retornoLLVM(f))
                    .append(" @").append(f.nombre())
                    .append('(').append(String.join(", ", params)).append(")\n");
        }
    }

    private static String llvmtipo(Tipo t) {
        return switch (t) {
            case Entero -> "i16";
            case Caracter -> "i8";
            case Cadena -> "ptr";
            case Void -> "void";
            case Error -> throw new IllegalStateException("Tipo Error en el generador");
        };
    }
    private static String retornoLLVM(Simbolo f) {
        if (f.nombre().equals("main")){
            return "i32";
        }
        return llvmtipo(f.tipo());
    }

    private static List<String> paramsLLVM(Simbolo f) {
        List<String> tipos = new ArrayList<>();
        for (Tipo t : f.tiposParametros()) {
            tipos.add(llvmtipo(t));
        }
        return tipos;
    }

    private static String promocionVariadica(Valor v) {
        return v.tipo().equals("ptr") ? "ptr" : "i32";
    }

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
        Bufferfunciones.append(" ").append(inst).append("\n");

    }

    private void terminar(String instruccion) {
        emitir(instruccion);
        terminado = true;
    }

    private void abrirBloque(String etq){
        if(!terminado)
            Bufferfunciones.append("br label %").append(etq).append("\n");
        Bufferfunciones.append(etq).append(":\n");
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

    private Valor generarIfSinElse(
            MicroCParser.StatementContext ctx) {

        Valor condicion = comoCondicion(visit(ctx.expression()));

        String etiquetaThen = nuevaEtiqueta("if_then");
        String etiquetaFin = nuevaEtiqueta("if_fin");

        terminar("br i1 " + condicion.ref()
                + ", label %" + etiquetaThen
                + ", label %" + etiquetaFin);

        abrirBloque(etiquetaThen);
        visit(ctx.statement(0));

        if (!terminado) {
            terminar("br label %" + etiquetaFin);
        }

        abrirBloque(etiquetaFin);
        return null;
    }

    private Valor generarIfConElse(
            MicroCParser.StatementContext ctx) {

        Valor condicion = comoCondicion(visit(ctx.expression()));

        String etiquetaThen = nuevaEtiqueta("if_then");
        String etiquetaElse = nuevaEtiqueta("if_else");
        String etiquetaFin = nuevaEtiqueta("if_fin");

        terminar("br i1 " + condicion.ref()
                + ", label %" + etiquetaThen
                + ", label %" + etiquetaElse);

        abrirBloque(etiquetaThen);
        visit(ctx.statement(0));

        if (!terminado) {
            terminar("br label %" + etiquetaFin);
        }

        abrirBloque(etiquetaElse);
        visit(ctx.statement(1));

        if (!terminado) {
            terminar("br label %" + etiquetaFin);
        }

        abrirBloque(etiquetaFin);
        return null;
    }

    private Valor generarWhile(
            MicroCParser.StatementContext ctx) {

        String etiquetaCondicion = nuevaEtiqueta("while_cond");
        String etiquetaCuerpo = nuevaEtiqueta("while_body");
        String etiquetaFin = nuevaEtiqueta("while_fin");

        abrirBloque(etiquetaCondicion);

        Valor condicion = comoCondicion(visit(ctx.expression()));

        terminar("br i1 " + condicion.ref()
                + ", label %" + etiquetaCuerpo
                + ", label %" + etiquetaFin);

        abrirBloque(etiquetaCuerpo);

        destinosBreak.push(etiquetaFin);
        destinosContinue.push(etiquetaCondicion);

        try {
            visit(ctx.statement(0));
        } finally {
            destinosBreak.pop();
            destinosContinue.pop();
        }

        if (!terminado) {
            terminar("br label %" + etiquetaCondicion);
        }

        abrirBloque(etiquetaFin);
        return null;
    }
}
