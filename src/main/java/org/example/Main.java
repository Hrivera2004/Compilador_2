package org.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.example.parser.*;

public class Main {
    public static void main(String[] args) {
        CharStream inputStream = CharStreams.fromString(" int main( void )\n" +
                    "{\n" +
                    "    return 0;\n" +
                    "}");
        MicroCLexer lexer= new MicroCLexer(inputStream);
        MicroCParser parser = new MicroCParser(new CommonTokenStream(lexer));

        ParseTree tree = parser.program();
        System.out.println("Sin errores de sintaxis.");
        System.out.println(tree.toStringTree(parser));
    }
}
