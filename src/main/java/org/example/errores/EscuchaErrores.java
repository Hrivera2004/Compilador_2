package org.example.errores;

import org.antlr.v4.runtime.*;

public class EscuchaErrores extends BaseErrorListener {

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg, RecognitionException e) {
        int columna = charPositionInLine + 1;
        String errorMsg;

        if (recognizer instanceof Lexer) {
            String texto = msg.substring(msg.indexOf("at: ") + 4);
            errorMsg = "Error léxico en línea " + line + ", columna " + columna + ": secuencia no reconocida " + texto;
        } else {
            Token token = (Token) offendingSymbol;
            errorMsg = (token.getType() == Token.EOF)
                    ? "Error sintáctico en línea " + line + ", columna " + columna + ": fin de archivo inesperado"
                    : "Error sintáctico en línea " + line + ", columna " + columna + ": token inesperado '" + token.getText() + "'";
        }

        throw new ErrorCompilacion(errorMsg);
    }
}
