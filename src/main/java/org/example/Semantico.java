package org.example;

import org.antlr.v4.runtime.Token;
import org.example.parser.MicroCBaseVisitor;
import org.example.parser.MicroCParser;

public class Semantico extends MicroCBaseVisitor<Void> {
    private final TablaSimbolos tabla = new TablaSimbolos();

    @Override
    public Void visitFunction_definition(
            MicroCParser.Function_definitionContext ctx ){
        tabla.entrarAmbito();

        try {
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

}
