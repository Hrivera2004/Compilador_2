package org.example.Semantico;

import org.antlr.v4.runtime.Token;
import org.example.enums.ClaseSimbolo;
import org.example.TablaSymbolos.Simbolo;
import org.example.TablaSymbolos.TablaSimbolos;
import org.example.enums.Tipo;
import org.example.errores.ErrorSemantico;
import org.example.parser.MicroCBaseVisitor;
import org.example.parser.MicroCParser;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class Semantico extends MicroCBaseVisitor<Void> {
    private final TablaSimbolos tabla = new TablaSimbolos();

    private int ciclosActivos = 0;

    private Tipo retornoActual = Tipo.Error;

    private final Set<String> funcionesDefinidas = new HashSet<>();

    public Semantico() {
        tabla.declarar(new Simbolo(
                "printf", Tipo.Void, ClaseSimbolo.Funcion,
                List.of(Tipo.Cadena), null, true));

        tabla.declarar(new Simbolo(
                "scanf", Tipo.Entero, ClaseSimbolo.Funcion,
                List.of(Tipo.Cadena), null, false));
    }

    @Override
    public Void visitFunction_definition(MicroCParser.Function_definitionContext ctx ){
        registrarFuncion(
                ctx.function_def_header().return_type(),
                ctx.function_def_header().identifier(),
                obtenerTiposParametros(ctx.function_def_header().parameters_def()),
                true,
                false
        );


        Tipo retornoAnterior = retornoActual;
        retornoActual = obtenerTipoRetorno(
                ctx.function_def_header().return_type()
        );
        tabla.entrarAmbito();

        try {
            declararParametros(ctx.function_def_header().parameters_def());
            visit(ctx.function_body());
        } finally {
            tabla.salirAmbito();
            retornoActual = retornoAnterior;
        }
        return null;
    }

    @Override
    public Void visitVariable_declaration(
            MicroCParser.Variable_declarationContext ctx ){
        Tipo tipo = obtenerTipo(ctx.type());
        declararIdentificadores(ctx.identifier_list(), tipo);
        return null;
    }

    private Tipo obtenerTipo(MicroCParser.TypeContext ctx) {
        return switch (ctx.getText()) {
            case "int" -> Tipo.Entero;
            case "char" -> Tipo.Caracter;
            default -> Tipo.Error;
        };
    }

    private void declararIdentificadores(
            MicroCParser.Identifier_listContext ctx,
            Tipo tipo) {

        if (ctx.identifier_list() != null) {
            declararIdentificadores(ctx.identifier_list(), tipo);
        }

        MicroCParser.IdentifierContext id = ctx.identifier();

        Simbolo simbolo = new Simbolo(
                id.getText(),
                tipo,
                ClaseSimbolo.Variable,
                java.util.List.of(),
                id.getStart(),
                false
        );

        if (!tabla.declarar(simbolo)) {
            error(id.getStart(),
                    "el identificador '" + id.getText()
                            + "' ya fue declarado en este ámbito");
        }
    }

    private void error(Token token, String mensaje) {
        throw new ErrorSemantico(
                "Error semántico en línea " + token.getLine()
                        + ", columna " + (token.getCharPositionInLine() + 1)
                        + ": " + mensaje
        );
    }

    private void declararParametros(
            MicroCParser.Parameters_defContext ctx) {

        if (ctx.parameter_def_list() != null) {
            declararParametros(ctx.parameter_def_list());
        }
    }
    private void declararParametros(
            MicroCParser.Parameter_def_listContext ctx) {

        if (ctx.parameter_def_list() != null) {
            declararParametros(ctx.parameter_def_list());
        }

        Tipo tipo = obtenerTipo(ctx.type());
        MicroCParser.IdentifierContext id = ctx.identifier();

        Simbolo simbolo = new Simbolo(
                id.getText(),
                tipo,
                ClaseSimbolo.Parametro,
                java.util.List.of(),
                id.getStart(),
                false
        );

        if (!tabla.declarar(simbolo)) {
            error(id.getStart(),
                    "el parámetro '" + id.getText()
                            + "' ya fue declarado en este ámbito");
        }
    }

    @Override
    public Void visitStatement(MicroCParser.StatementContext ctx) {
        String primerElemento = ctx.getChild(0).getText();

        if ("while".equals(primerElemento)) {
            validarCondicion(ctx.expression());

            visit(ctx.expression());

            ciclosActivos++;
            try {
                visit(ctx.statement(0));
            } finally {
                ciclosActivos--;
            }

            return null;
        }

        if ("if".equals(primerElemento)) {
            validarCondicion(ctx.expression());

            return visitChildren(ctx);
        }

        if ("break".equals(primerElemento)) {
            if (ciclosActivos == 0) {
                error(ctx.getStart(),
                        "'break' solo puede utilizarse dentro de un while");
            }
            return null;
        }

        if ("continue".equals(primerElemento)) {
            if (ciclosActivos == 0) {
                error(ctx.getStart(),
                        "'continue' solo puede utilizarse dentro de un while");
            }
            return null;
        }

        if ("return".equals(primerElemento)) {
            if (ctx.expression() == null) {
                if (retornoActual != Tipo.Void) {
                    error(ctx.getStart(),
                            "la función debe retornar un valor de tipo "
                                    + retornoActual);
                }
            } else {
                Tipo devuelto = tipoDeExpresion(ctx.expression());

                if (retornoActual == Tipo.Void) {
                    error(ctx.getStart(),
                            "una función void no puede retornar un valor");
                }

                if (retornoActual != devuelto) {
                    error(ctx.getStart(),
                            "el return debe ser de tipo " + retornoActual
                                    + ", pero recibió " + devuelto);
                }
            }

            return visitChildren(ctx);
        }

        if (ctx.expression() != null) {
            tipoDeExpresion(ctx.expression());
        }

        return visitChildren(ctx);
    }

    private Tipo obtenerTipoRetorno(
            MicroCParser.Return_typeContext ctx) {

        if ("void".equals(ctx.getText())) {
            return Tipo.Void;
        }

        return obtenerTipo(ctx.type());
    }

    private List<Tipo> obtenerTiposParametros(
            MicroCParser.Parameters_defContext ctx) {

        if (ctx.parameter_def_list() == null) {
            return List.of();
        }

        return obtenerTiposParametros(ctx.parameter_def_list());
    }

    private List<Tipo> obtenerTiposParametros(
            MicroCParser.Parameter_def_listContext ctx) {

        List<Tipo> tipos = new ArrayList<>();

        if (ctx.parameter_def_list() != null) {
            tipos.addAll(obtenerTiposParametros(ctx.parameter_def_list()));
        }

        tipos.add(obtenerTipo(ctx.type()));

        return tipos;
    }

    private void registrarFuncion(
            MicroCParser.Return_typeContext retornoCtx,
            MicroCParser.IdentifierContext id,
            List<Tipo> parametros,
            boolean esDefinicion, boolean esVariadica) {

        if (esPredefinida(id.getText())){
            if (esDefinicion){
                error(id.getStart(),
                        "no se puede redefinir la función predefinida '" + id.getText() + "'");
            }
            return;
        }

        Tipo retorno = obtenerTipoRetorno(retornoCtx);
        Simbolo existente = tabla.resolver(id.getText());



        if (existente == null) {
            Simbolo funcion = new Simbolo(
                    id.getText(),
                    retorno,
                    ClaseSimbolo.Funcion,
                    parametros,
                    id.getStart(),
                    esVariadica
            );

            tabla.declarar(funcion);
        } else {
            if (!existente.esFuncion()) {
                error(id.getStart(),
                        "'" + id.getText()
                                + "' ya fue declarado y no es una función");
            }

            boolean firmaDistinta =
                    existente.tipo() != retorno
                            || !existente.tiposParametros().equals(parametros)
                            || existente.variadica() != esVariadica;

            if (firmaDistinta) {
                error(id.getStart(),
                        "la función '" + id.getText()
                                + "' tiene una firma distinta a su prototipo");
            }
        }

        if (esDefinicion && !funcionesDefinidas.add(id.getText())) {
            error(id.getStart(),
                    "la función '" + id.getText()
                            + "' ya fue definida");
        }
    }

    @Override
    public Void visitFunction_declaration(
            MicroCParser.Function_declarationContext ctx) {

        registrarFuncion(
                ctx.return_type(),
                ctx.identifier(),
                obtenerTiposParametros(ctx.parameters_decl()),
                false,
                esVariadica(ctx.parameters_decl())
                );

        return null;
    }

    private List<Tipo> obtenerTiposParametros(
            MicroCParser.Parameters_declContext ctx) {

        if (ctx.parameter_decl_list() == null) {
            return List.of(); // void
        }

        return obtenerTiposParametros(ctx.parameter_decl_list());
    }

    private List<Tipo> obtenerTiposParametros(
            MicroCParser.Parameter_decl_listContext ctx) {

        List<Tipo> tipos = new ArrayList<>();

        if (ctx.parameter_decl_list() != null) {
            tipos.addAll(obtenerTiposParametros(ctx.parameter_decl_list()));
        }

        MicroCParser.Parameter_decl_specContext parametro =
                ctx.parameter_decl_spec();

        if (parametro.type() == null) {
            tipos.add(Tipo.Cadena);          // 'char' '*' identifier
        } else {
            tipos.add(obtenerTipo(parametro.type()));
        }

        return tipos;
    }

    @Override
    public Void visitProgram(MicroCParser.ProgramContext ctx) {
        visitChildren(ctx);

        Simbolo main = tabla.resolver("main");

        if (main == null) {
            error(ctx.getStart(),
                    "el programa debe declarar una función llamada 'main'");
        }

        if (!main.esFuncion()) {
            error(main.declaracion(),
                    "'main' debe ser una función");
        }

        if (!main.tiposParametros().isEmpty()) {
            error(main.declaracion(),
                    "'main' no debe recibir parámetros");
        }

        if (!funcionesDefinidas.contains("main")) {
            error(main.declaracion(),
                    "'main' debe tener una definición");
        }

        return null;
    }

    @Override
    public Void visitFactor(MicroCParser.FactorContext ctx) {
        if (ctx.identifier() == null) {
            return visitChildren(ctx);
        }

        MicroCParser.IdentifierContext id = ctx.identifier();
        Simbolo simbolo = tabla.resolver(id.getText());



        if (simbolo == null) {
            error(id.getStart(),
                    "el identificador '" + id.getText()
                            + "' no ha sido declarado");
        }

        boolean esLlamada =
                ctx.getChildCount() > 1
                        && "(".equals(ctx.getChild(1).getText());

        if (!esLlamada) {
            if (simbolo.esFuncion()) {
                error(id.getStart(),
                        "la función '" + id.getText()
                                + "' debe llamarse usando paréntesis");
            }

            return visitChildren(ctx);
        }

        if (!simbolo.esFuncion()) {
            error(id.getStart(),
                    "'" + id.getText() + "' no es una función");
        }
        if (esPredefinida(id.getText())) {
            if (id.getText().equals("printf")) {
                validarPrintf(ctx);
            } else {
                validarScanf(ctx);
            }
            return visitChildren(ctx);
        }

        List<MicroCParser.ExpressionContext> argumentos =
                obtenerArgumentos(ctx.expression_list());

        int fijos = simbolo.tiposParametros().size();
        boolean faltan = argumentos.size() < fijos;
        boolean sobran = !simbolo.variadica() && argumentos.size() > fijos;

        if (faltan || sobran) {
            error(id.getStart(),
                    "la función '" + id.getText() + "' espera "
                            + (simbolo.variadica() ? "al menos " : "")
                            + fijos + " argumento(s), pero recibió "
                            + argumentos.size());
        }

        for (int i = 0; i < fijos; i++) {
            Tipo esperado = simbolo.tiposParametros().get(i);
            Tipo recibido = tipoDeExpresion(argumentos.get(i));

            if (esperado != recibido) {
                error(id.getStart(),
                        "el argumento " + (i + 1)
                                + " de '" + id.getText()
                                + "' debe ser de tipo " + esperado
                                + ", pero recibió " + recibido);
            }
        }

        return visitChildren(ctx);
    }

    private List<MicroCParser.ExpressionContext> obtenerArgumentos(
            MicroCParser.Expression_listContext ctx) {

        if (ctx == null) {
            return List.of(); // llamada como funcion()
        }

        List<MicroCParser.ExpressionContext> argumentos = new ArrayList<>();

        if (ctx.expression_list() != null) {
            argumentos.addAll(obtenerArgumentos(ctx.expression_list()));
        }

        argumentos.add(ctx.expression());

        return argumentos;
    }

    private Tipo obtenerTipoConstante(
            MicroCParser.ConstantContext ctx) {

        if (ctx.numeric_constant() != null) {
            return Tipo.Entero;
        }

        if (ctx.char_constant() != null) {
            return Tipo.Caracter;
        }

        if (ctx.string_constant() != null) {
            return Tipo.Cadena;
        }

        return Tipo.Error;
    }

    private Tipo tipoDeExpresion(
            MicroCParser.ExpressionContext ctx) {

        Tipo izquierdo = tipoDeEquality(ctx.equality_expression(0));

        if (ctx.equality_expression().size() == 1) {
            return izquierdo;
        }

        if (!esAsignable(ctx.equality_expression(0))) {
            error(ctx.getStart(),
                    "el lado izquierdo de una asignación debe ser una variable");
        }

        Tipo derecho = tipoDeEquality(ctx.equality_expression(1));

        if (izquierdo != derecho) {
            error(ctx.getStart(),
                    "no se puede asignar un valor de tipo "
                            + derecho + " a una expresión de tipo "
                            + izquierdo);
        }

        return izquierdo;
    }

    private Tipo tipoDeEquality(MicroCParser.Equality_expressionContext ctx) {
        if (ctx.eq_op() == null) {
            return tipoDeRelational(ctx.relational_expression(0));
        }

        Tipo izquierdo = tipoDeRelational(ctx.relational_expression(0));
        Tipo derecho = tipoDeRelational(ctx.relational_expression(1));

        return operarComparacion(izquierdo, derecho, ctx.eq_op().getStart());
    }

    private Tipo tipoDeRelational(MicroCParser.Relational_expressionContext ctx) {

        if (ctx.rel_op() == null) {
            return tipoDeSimple(ctx.simple_expression(0));
        }

        Tipo izquierdo = tipoDeSimple(ctx.simple_expression(0));
        Tipo derecho = tipoDeSimple(ctx.simple_expression(1));

        return operarComparacion(izquierdo, derecho, ctx.rel_op().getStart());
    }

    private Tipo tipoDeSimple(
            MicroCParser.Simple_expressionContext ctx) {

        if (ctx.add_op() == null) {
            return tipoDeTerm(ctx.term());
        }

        Tipo izquierdo = tipoDeSimple(ctx.simple_expression());
        Tipo derecho = tipoDeTerm(ctx.term());

        return operarAritmetica(
                izquierdo,
                derecho,
                ctx.add_op().getStart()
        );
    }

    private Tipo tipoDeTerm(
            MicroCParser.TermContext ctx) {

        if (ctx.mul_op() == null) {
            return tipoDeFactor(ctx.factor());
        }

        Tipo izquierdo = tipoDeTerm(ctx.term());
        Tipo derecho = tipoDeFactor(ctx.factor());

        return operarAritmetica(
                izquierdo,
                derecho,
                ctx.mul_op().getStart()
        );
    }

    private Tipo tipoDeFactor(
            MicroCParser.FactorContext ctx) {

        if (ctx.constant() != null) {
            return obtenerTipoConstante(ctx.constant());
        }

        if (ctx.identifier() != null) {
            Simbolo simbolo = tabla.resolver(ctx.identifier().getText());

            if (simbolo == null) {
                error(ctx.identifier().getStart(),
                        "el identificador '" + ctx.identifier().getText()
                                + "' no ha sido declarado");
            }

            boolean esLLamada = (ctx.getChildCount() > 1)
                    && ("(".equals(ctx.getChild(1).getText()));

            if (esLLamada && simbolo.esFuncion() && ctx.identifier().getText().equals("scanf"))
                return validarScanf(ctx);

            return simbolo.tipo();
        }

        if (ctx.expression() != null) {
            return tipoDeExpresion(ctx.expression());
        }

        if (ctx.factor() != null) {
            Tipo tipo = tipoDeFactor(ctx.factor());

            if (ctx.add_op() != null && tipo != Tipo.Entero) {
                error(ctx.add_op().getStart(),
                        "el operador '" + ctx.add_op().getText()
                                + "' solo acepta un operando de tipo Entero");
            }

            return tipo;
        }

        return Tipo.Error;
    }

    private boolean esAsignable(
            MicroCParser.Equality_expressionContext ctx) {

        if (ctx.eq_op() != null) {
            return false;
        }

        MicroCParser.Relational_expressionContext relacional =
                ctx.relational_expression(0);

        if (relacional.rel_op() != null) {
            return false;
        }

        MicroCParser.Simple_expressionContext simple =
                relacional.simple_expression(0);

        if (simple.add_op() != null) {
            return false;
        }

        MicroCParser.TermContext termino = simple.term();

        if (termino.mul_op() != null) {
            return false;
        }

        MicroCParser.FactorContext factor = termino.factor();

        return factor.identifier() != null
                && factor.getChildCount() == 1;
    }

    private boolean esVariadica(MicroCParser.Parameters_declContext ctx) {
        int ultimo = ctx.getChildCount() - 1;
        return ctx.getChild(ultimo).getText().equals("...");
    }

    private boolean esPredefinida(String nombre) {
        return nombre.equals("printf") || nombre.equals("scanf");
    }

    private Tipo operarAritmetica(
            Tipo izquierdo,
            Tipo derecho,
            Token operador) {

        if (izquierdo != Tipo.Entero || derecho != Tipo.Entero) {
            error(operador,
                    "el operador '" + operador.getText()
                            + "' solo acepta operandos de tipo Entero");
        }

        return Tipo.Entero;
    }

    private Tipo operarComparacion(Tipo izquierdo, Tipo derecho, Token operador) {
        boolean comparable = izquierdo == Tipo.Entero
                        || izquierdo == Tipo.Caracter;

        if (!comparable || izquierdo != derecho) {
            error(operador,
                    "el operador '" + operador.getText()
                            + "' solo compara operandos del mismo tipo (Entero o Caracter), pero recibió "
                            + izquierdo + " y " + derecho);
        }

        return Tipo.Entero;
    }

    private void validarCondicion(MicroCParser.ExpressionContext ctx) {
        Tipo tipo = tipoDeExpresion(ctx);

        if (tipo != Tipo.Entero && tipo != Tipo.Caracter) {
            error(ctx.getStart(),
                    "la condición debe ser de tipo Entero o Caracter, pero recibió " + tipo);
        }
    }


    private void validarPrintf(MicroCParser.FactorContext ctx){
        Token nombre = ctx.identifier().getStart();
        List<MicroCParser.ExpressionContext> args = obtenerArgumentos(ctx.expression_list());
        if (args.isEmpty() || args.size() > 2) {
            error(nombre, "printf recibe  1 o 2 argumento, pero recibio "
                    + args.size());
        }
        MicroCParser.ExpressionContext primero = args.getFirst();
        String formato = textoDeFormato(primero);


        if (formato == null) {
            error(args.getFirst().getStart(),
                    "el formato de printf debe ser una constante de cadena");
        }

        int conversiones = 0;
        char conversion = ' ';
        for (int i = 0; i < formato.length(); i++) {
            if (formato.charAt(i) != '%') {
                continue;
            }

            if (i + 1 >= formato.length()) {
                error(primero.getStart(),
                        "el formato de printf termina con un '%' suelto");
            }

            char siguiente = formato.charAt(i + 1);
            i++;                                  // salta el carácter después del %

            if (siguiente == '%') {
                continue;                         // %% imprime un porcentaje
            }

            if (siguiente != 'd' && siguiente != 'c') {
                error(primero.getStart(),
                        "conversión inválida '%" + siguiente
                                + "' en printf (solo %d o %c)");
            }

            conversiones++;
            conversion = siguiente;
        }

        if (conversiones > 1) {
            error(primero.getStart(),
                    "printf solo permite una conversión (%d o %c)");
        }

        if (conversiones == 0 && args.size() == 2) {
            error(args.get(1).getStart(),
                    "printf sin conversión no recibe un segundo argumento");
        }

        if (conversiones == 1) {
            if (args.size() == 1) {
                error(nombre, "printf espera un segundo argumento para %" + conversion);
            }

            Tipo esperado = (conversion == 'd') ? Tipo.Entero : Tipo.Caracter;
            Tipo recibido = tipoDeExpresion(args.get(1));

            if (esperado != recibido) {
                error(args.get(1).getStart(),
                        "%" + conversion + " espera un valor de tipo " + esperado
                                + ", pero recibió " + recibido);
            }
        }
    }

    private Tipo validarScanf(MicroCParser.FactorContext ctx){
        Token nombre = ctx.identifier().getStart();
        List<MicroCParser.ExpressionContext> args = obtenerArgumentos(ctx.expression_list());
        if (args.size() != 1) {
            error(nombre, "scanf recibe exactamente 1 argumento, pero recibio "
                    + args.size());
        }
        String formato = textoDeFormato(args.getFirst());
        if (formato == null) {
            error(args.getFirst().getStart(),
                    "el formato de scanf debe ser una constante de cadena");
        }
        if (formato.equals("%d")) return Tipo.Entero;
        if (formato.equals("%c")) return Tipo.Caracter;

        error(args.getFirst().getStart(),
                "scanf solo acepta \"%d\" o \"%c\" como formato");
        return Tipo.Error;
    }

    private String textoDeFormato(MicroCParser.ExpressionContext ctx) {
        if (ctx.equality_expression().size() != 1) {
            return null;                    // es una asignación: x = "..."
        }
        MicroCParser.Equality_expressionContext igualdad = ctx.equality_expression(0);
        if (igualdad.eq_op() != null) {
            return null;                    // tiene == o !=
        }

        MicroCParser.Relational_expressionContext relacional = igualdad.relational_expression(0);
        if (relacional.rel_op() != null) {
            return null;                    // tiene <, <=, > o >=
        }

        MicroCParser.Simple_expressionContext simple = relacional.simple_expression(0);
        if (simple.add_op() != null) {
            return null;                    // tiene + o -
        }

        MicroCParser.TermContext termino = simple.term();
        if (termino.mul_op() != null) {
            return null;                    // tiene * o /
        }

        MicroCParser.FactorContext factor = termino.factor();
        if (factor.constant() == null || factor.constant().string_constant() == null) {
            return null;                    // no es una constante de cadena
        }

        String texto = factor.constant().string_constant().getText();
        return texto.substring(1, texto.length() - 1);   // quita las comillas
    }
}
