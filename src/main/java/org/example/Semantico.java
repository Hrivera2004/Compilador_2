package org.example;

import org.antlr.v4.runtime.Token;
import org.example.parser.MicroCBaseVisitor;
import org.example.parser.MicroCParser;

public class Semantico extends MicroCBaseVisitor<Void> {
    //--Checks--
    @Override
    public Void visitIdentifier(MicroCParser.IdentifierContext ctx) {
        if (ctx.getText().length() > 12)
            error(ctx.getStart(), "'" + ctx.getText() + "' pasa de 12 caracteres");
        return null;
    }

    @Override
    public Void visitInteger_constant(MicroCParser.Integer_constantContext ctx) {
        RevisaRango_NUMEROS(ctx, false);
        return null;
    }

    //--Ayudantes--
    private void RevisaRango_NUMEROS(MicroCParser.Integer_constantContext ctx, boolean negativo){
        String texto = ctx.getText();
        Boolean esHex = texto.startsWith("0x");
        long valor;

        try {
            valor = esHex? Long.parseLong(texto.substring(2),16) : Long.parseLong(texto);
        } catch (NumberFormatException e){
            valor = 32769;
        }

        long maximo = esHex ? 0xFFFF : (negativo ? 32768 : 32767);
        if(valor > maximo){
            String mostrado = (negativo ? "-" : "") + texto;
            error(ctx.getStart(), "la constante " + mostrado + " no cabe en 16 bits"
                    + (esHex ? " (máximo 0xFFFF)" : " (rango -32768 a 32767)"));
        }

    }

    private void error(Token t, String mensaje) {
        throw new ErrorSemantico("Error semántico en línea " + t.getLine()
                + ", columna " + (t.getCharPositionInLine() + 1) + ": " + mensaje);
    }

    public static class ErrorSemantico extends RuntimeException {
        public ErrorSemantico(String mensaje) {
            super(mensaje);
        }
    }
}
