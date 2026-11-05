package org.example;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.example.Semantico.CheckLexer;
import org.example.Semantico.Semantico;
import org.example.errores.EscuchaErrores;
import org.example.parser.MicroCLexer;
import org.example.parser.MicroCParser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
public class frontend extends JFrame {

    private final JTextArea consola = new JTextArea();
    private final JLabel jLabel_file = new JLabel("-");
    private final JButton jButton_load = boton("Cargar archivo");
    private final JButton jButton_clear = boton("Limpiar");

    public frontend() {
        super("Compilador Micro-C");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Barra superior (mismo color que el frontend original)
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        barra.setBackground(new Color(0, 102, 102));
        JLabel titulo = new JLabel("Micro-C");
        titulo.setFont(new Font("Segoe UI", Font.BOLD | Font.ITALIC, 22));
        titulo.setForeground(Color.WHITE);
        jLabel_file.setForeground(Color.WHITE);
        jLabel_file.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        barra.add(titulo);
        barra.add(jButton_load);
        barra.add(jButton_clear);
        barra.add(new JLabel("  "));
        barra.add(jLabel_file);

        // Consola
        consola.setEditable(false);
        consola.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        consola.setBackground(new Color(30, 30, 30));
        consola.setForeground(new Color(220, 220, 220));
        consola.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(consola);
        scroll.setPreferredSize(new Dimension(800, 500));

        add(barra, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        jButton_load.addActionListener(e -> cargarArchivo());
        jButton_clear.addActionListener(e -> {
            consola.setText("");
            jLabel_file.setText("-");
        });

        redirigirConsola();
        pack();
        setLocationRelativeTo(null);
    }

    private void cargarArchivo() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Micro-C (*.c, *.mc, *.txt)", "c", "mc", "txt"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = fc.getSelectedFile();
        jLabel_file.setText(archivo.getName());
        consola.setText("");
        jButton_load.setEnabled(false);

        // Se compila en segundo plano para que la ventana no se congele
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    compilar(archivo);
                } catch (Exception ex) {
                    System.err.println("Error: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                jButton_load.setEnabled(true);
            }
        }.execute();
    }

    /**
     * AQUÍ se conecta el compilador. Todo lo que se imprima con
     * System.out / System.err aparece en la consola de la ventana.
     */
    private void compilar(File archivo) throws Exception {
        String codigo = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
        System.out.println("Compilando " + archivo.getName() + "...\n");

        CharStream inputStream = CharStreams.fromString(codigo);
        EscuchaErrores escucha = new EscuchaErrores();

        //se crea el lexer con un error listener
        MicroCLexer lexer= new MicroCLexer(inputStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(escucha);

        //se crea el parser con un error listener
        MicroCParser parser = new MicroCParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(escucha);

        ParseTree tree = parser.program();
        new CheckLexer().visit(tree);
        new Semantico().visit(tree);

        System.out.println("Sin errores léxicos, sintácticos ni semánticos.");
        System.out.println(tree.toStringTree(parser));
        System.out.println(codigo);

        System.out.println("\n-> Terminado");
    }

    /** Manda System.out y System.err al JTextArea (y también a la terminal). */
    private void redirigirConsola() {
        PrintStream terminal = System.out;
        OutputStream os = new OutputStream() {
            @Override
            public void write(int b) {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                String s = new String(b, off, len, StandardCharsets.UTF_8);
                terminal.print(s);
                SwingUtilities.invokeLater(() -> {
                    consola.append(s);
                    consola.setCaretPosition(consola.getDocument().getLength());
                });
            }
        };
        PrintStream ps = new PrintStream(os, true, StandardCharsets.UTF_8);
        System.setOut(ps);
        System.setErr(ps);
    }

    private static JButton boton(String texto) {
        JButton b = new JButton(texto);
        b.setBackground(Color.WHITE);
        b.setForeground(new Color(102, 102, 102));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        return b;
    }

}
