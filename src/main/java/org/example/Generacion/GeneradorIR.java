package org.example.Generacion;

import org.antlr.v4.runtime.tree.ParseTree;
import org.example.parser.MicroCBaseVisitor;
import org.example.parser.MicroCParser;

import java.util.ArrayList;
import java.util.List;

public class GeneradorIR extends MicroCBaseVisitor<String> {

    //buffers para cargar el flujo
    private final StringBuilder declaraciones = new StringBuilder(); // declaraciones
    private final StringBuilder strings = new StringBuilder(); // constantes de string
    private final StringBuilder globales = new StringBuilder(); // variables globales
    private final StringBuilder funciones = new StringBuilder(); // define de cada función

    //cariables del estado de las funciones
    private int contTemp;
    private int contEtiqueta;
    private boolean terminado;
    private String tipoRetorno;

    //Metodo principal
    public String generar(ParseTree arbol) {
        visit(arbol);

        StringBuilder modulo = new StringBuilder("; Micro-C\n\n");
        for (StringBuilder seccion : List.of(declaraciones, strings, globales)) {
            if (!seccion.isEmpty()) {
                modulo.append(seccion).append('\n');
            }
        }
        modulo.append(funciones);
        return modulo.toString();
    }

    //Funciones
    @Override
    public String  visitFunction_definition(MicroCParser.Function_definitionContext ctx){
        var header = ctx.function_def_header();
        String nombre = header.identifier().getText();
        //main siempre es i32 : sino se retorna el tipo del header
        tipoRetorno = (nombre.equals("main")) ? "i32":tipoRetornoLLVM(header.return_type());

        contEtiqueta = 0;
        contTemp = 0;

        List<String> params = new ArrayList<>();
        if(header.parameters_def().parameter_def_list() !=null){
            juntarParametros(header.parameters_def().parameter_def_list(), params);
        }

        funciones.append("define ").append(tipoRetorno).append(" @").append(nombre)
                .append('(').append(String.join(", ", params)).append(") {\n");
        funciones.append("entry:\n");
        terminado = false;

        visit(ctx.function_body());

        if (!terminado) {
            retornoPorDefecto();
        }

        funciones.append("}\n\n");
        return null;

    }
    private void juntarParametros(MicroCParser.Parameter_def_listContext lista, List<String> out) {
        if (lista.parameter_def_list() != null) {
            juntarParametros(lista.parameter_def_list(), out);
        }
        out.add(tipoLLVM(lista.type()) + " %" + lista.identifier().getText());
    }

    private void retornoPorDefecto() {
        if (tipoRetorno.equals("void")) {
            terminar("ret void");
        } else {
            terminar("ret " + tipoRetorno + " 0");
        }
    }
    //helpers

    private void emitir(String inst){
        if (terminado)
            abrirBloque(nuevaEtiqueta("muerto"));
        funciones.append(" ").append(inst).append("\n");

    }

    private void terminar(String instruccion) {
        emitir(instruccion);
        terminado = true;
    }

    private void abrirBloque(String etq){
        if(!terminado)
            funciones.append("br label %").append(etq).append("\n");
        funciones.append(etq).append(":\n");
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
    static String tipoRetornoLLVM(MicroCParser.Return_typeContext  ret){
        return ret.type() == null ? "void" : tipoLLVM(ret.type());
    }

}
