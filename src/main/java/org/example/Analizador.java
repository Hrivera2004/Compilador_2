package org.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.example.Semantico.CheckLexer;
import org.example.Semantico.Semantico;
import org.example.errores.*;
import org.example.parser.*;

public class Analizador {
    // devuelve null si las cosas funcionan
    public static String analizar(String codigo) {
        BaseErrorListener fallar = new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?,?> r, Object tok, int linea,
                                              int col, String msg, RecognitionException e) {
                throw new ErrorSemantico("Error sintáctico en línea " + linea
                        + ", columna " + (col + 1) + ": " + msg);
            }
        };
        try {
            MicroCLexer lexer = new MicroCLexer(CharStreams.fromString(codigo));
            lexer.removeErrorListeners(); lexer.addErrorListener(fallar);
            MicroCParser parser = new MicroCParser(new CommonTokenStream(lexer));
            parser.removeErrorListeners(); parser.addErrorListener(fallar);
            ParseTree tree = parser.program();
            new CheckLexer().visit(tree);
            new Semantico().visit(tree);
            return null;
        } catch (ErrorSemantico e) {
            return e.getMessage();
        }
    }
}
