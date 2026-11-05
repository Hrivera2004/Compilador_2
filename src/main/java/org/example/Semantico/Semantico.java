package org.example.Semantico;

import org.antlr.v4.runtime.Token;
import org.example.TablaSymbolos.ClaseSimbolo;
import org.example.TablaSymbolos.Simbolo;
import org.example.TablaSymbolos.TablaSimbolos;
import org.example.TablaSymbolos.Tipo;
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

    @Override
    public Void visitFunction_definition(
            MicroCParser.Function_definitionContext ctx ){
        registrarFuncion(
                ctx.function_def_header().return_type(),
                ctx.function_def_header().identifier(),
                obtenerTiposParametros(ctx.function_def_header().parameters_def()),
                true
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
                id.getStart()
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
                id.getStart()
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
            visit(ctx.expression());

            ciclosActivos++;
            try {
                visit(ctx.statement(0));
            } finally {
                ciclosActivos--;
            }

            return null;
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
            boolean esDefinicion) {

        Tipo retorno = obtenerTipoRetorno(retornoCtx);
        Simbolo existente = tabla.resolver(id.getText());

        if (existente == null) {
            Simbolo funcion = new Simbolo(
                    id.getText(),
                    retorno,
                    ClaseSimbolo.Funcion,
                    parametros,
                    id.getStart()
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
                            || !existente.tiposParametros().equals(parametros);

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
                false
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

        tipos.add(obtenerTipo(parametro.type()));

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

        List<MicroCParser.ExpressionContext> argumentos =
                obtenerArgumentos(ctx.expression_list());

        if (argumentos.size() != simbolo.tiposParametros().size()) {
            error(id.getStart(),
                    "la función '" + id.getText() + "' espera "
                            + simbolo.tiposParametros().size()
                            + " argumento(s), pero recibió "
                            + argumentos.size());
        }
        for (int i = 0; i < argumentos.size(); i++) {
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

    private Tipo tipoDeEquality(
            MicroCParser.Equality_expressionContext ctx) {

        if (ctx.eq_op() == null) {
            return tipoDeRelational(ctx.relational_expression(0));
        }

        return Tipo.Entero;
    }

    private Tipo tipoDeRelational(
            MicroCParser.Relational_expressionContext ctx) {

        if (ctx.rel_op() == null) {
            return tipoDeSimple(ctx.simple_expression(0));
        }

        return Tipo.Entero;
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
}
