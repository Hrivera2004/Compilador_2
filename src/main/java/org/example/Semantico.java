package org.example;

import org.antlr.v4.runtime.Token;
import org.example.parser.MicroCBaseVisitor;
import org.example.parser.MicroCParser;

public class Semantico extends MicroCBaseVisitor<Void> {

    private void error(Token t, String mensaje) {
        throw new ErrorSemantico("Error semántico en línea " + t.getLine()
                + ", columna " + (t.getCharPositionInLine() + 1) + ": " + mensaje);
    }

}
