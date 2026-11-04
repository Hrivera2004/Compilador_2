package org.example;

import org.antlr.v4.runtime.Token;
import org.example.parser.MicroCBaseVisitor;
import org.example.parser.MicroCParser;
import java.util.ArrayList;
import java.util.List;

public class Semantico extends MicroCBaseVisitor<Void> {
    private final TablaSimbolos tabla = new TablaSimbolos();

    private int ciclosActivos = 0;

    @Override
    public Void visitFunction_definition(
            MicroCParser.Function_definitionContext ctx ){
        registrarFuncion(ctx.function_def_header());
        tabla.entrarAmbito();

        try {
            declararParametros(ctx.function_def_header().parameters_def());
            visit(ctx.function_body());
        } finally {
            tabla.salirAmbito();
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
        throw new CheckLexer.ErrorSemantico(
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
            MicroCParser.Function_def_headerContext ctx) {

        Tipo retorno = obtenerTipoRetorno(ctx.return_type());
        List<Tipo> parametros = obtenerTiposParametros(ctx.parameters_def());

        MicroCParser.IdentifierContext id = ctx.identifier();

        Simbolo funcion = new Simbolo(
                id.getText(),
                retorno,
                ClaseSimbolo.Funcion,
                parametros,
                id.getStart()
        );

        if (!tabla.declarar(funcion)) {
            error(id.getStart(),
                    "la función '" + id.getText()
                            + "' ya fue declarada");
        }
    }
}
