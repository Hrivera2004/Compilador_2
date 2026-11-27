package org.example;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.example.Generacion.GeneradorIR;
import org.example.Semantico.CheckLexer;
import org.example.Semantico.Semantico;
import org.example.errores.ErrorCompilacion;
import org.example.errores.ErrorSemantico;
import org.example.errores.EscuchaErrores;
import org.example.parser.MicroCLexer;
import org.example.parser.MicroCParser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

public class frontend extends JFrame {

    private static final Color FONDO_OSCURO = new Color(30, 30, 30);
    private static final Color TEXTO_CLARO = new Color(220, 220, 220);
    private static final Color ROJO_CLARO = new Color(255, 205, 205);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 14);

    private static final int TAB_FUENTE = 0;
    private static final int TAB_CONSOLA = 3;

    private final JLabel jLabel_file = new JLabel("-");
    private final JButton jButton_load = boton("Cargar");
    private final JButton jButton_compile = boton("Compilar");
    private final JButton jButton_run = boton("Ejecutar");

    private final JTabbedPane pestanas = new JTabbedPane();
    private final JTextArea editor = new JTextArea();
    private final JTextArea numerosLinea = new JTextArea("1");
    private final JTextArea ir = new JTextArea();
    private final JTextArea irOptimizado = new JTextArea();
    private final JTextArea consola = new JTextArea();
    private final JTextField entrada = new JTextField();

    /** Archivo fuente cargado y último .ll generado con éxito. */
    private File archivoActual;
    private File llActual;

    /** Programa en ejecución (null si no hay ninguno). */
    private volatile Process proceso;

    /** Marca del resaltado de error en el editor (null si no hay). */
    private Object resaltado;

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
        barra.add(jButton_compile);
        barra.add(jButton_run);
        barra.add(new JLabel("  "));
        barra.add(jLabel_file);
        jButton_compile.setEnabled(false);
        jButton_run.setEnabled(false);

        // Pestañas
        pestanas.addTab("Fuente", crearEditor());
        pestanas.addTab("IR", new JScrollPane(areaSoloLectura(ir)));
        pestanas.addTab("IR optimizado", new JScrollPane(areaSoloLectura(irOptimizado)));
        pestanas.addTab("Consola", crearConsola());
        pestanas.setPreferredSize(new Dimension(900, 600));
        mostrarIROptimizado("Optimización pendiente");

        add(barra, BorderLayout.NORTH);
        add(pestanas, BorderLayout.CENTER);

        jButton_load.addActionListener(e -> cargarArchivo());
        jButton_compile.addActionListener(e -> compilar());
        jButton_run.addActionListener(e -> ejecutar());
        entrada.addActionListener(e -> enviarEntrada());

        // Si se cierra la ventana con un programa corriendo, se termina también
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Process p = proceso;
                if (p != null) {
                    p.destroyForcibly();
                }
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------
    // Construcción de componentes
    // ------------------------------------------------------------------

    private JScrollPane crearEditor() {
        editor.setFont(MONO);
        editor.setTabSize(4);
        editor.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

        numerosLinea.setFont(MONO);
        numerosLinea.setEditable(false);
        numerosLinea.setFocusable(false);
        numerosLinea.setBackground(new Color(235, 235, 235));
        numerosLinea.setForeground(new Color(102, 102, 102));
        numerosLinea.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { actualizarNumerosLinea(); }
            @Override public void removeUpdate(DocumentEvent e) { actualizarNumerosLinea(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });

        JScrollPane scroll = new JScrollPane(editor);
        scroll.setRowHeaderView(numerosLinea);
        return scroll;
    }

    private JPanel crearConsola() {
        areaSoloLectura(consola);
        consola.setBackground(FONDO_OSCURO);
        consola.setForeground(TEXTO_CLARO);
        consola.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        entrada.setFont(MONO);
        entrada.setBackground(FONDO_OSCURO);
        entrada.setForeground(TEXTO_CLARO);
        entrada.setCaretColor(TEXTO_CLARO);
        entrada.setEnabled(false);
        entrada.setToolTipText("Entrada del programa (Enter para enviar)");

        JLabel etiqueta = new JLabel(" Entrada: ");
        etiqueta.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JPanel filaEntrada = new JPanel(new BorderLayout());
        filaEntrada.add(etiqueta, BorderLayout.WEST);
        filaEntrada.add(entrada, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(consola), BorderLayout.CENTER);
        panel.add(filaEntrada, BorderLayout.SOUTH);
        return panel;
    }

    private static JTextArea areaSoloLectura(JTextArea area) {
        area.setEditable(false);
        area.setFont(MONO);
        area.setTabSize(4);
        return area;
    }

    private void actualizarNumerosLinea() {
        int lineas = editor.getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lineas; i++) {
            sb.append(i);
            if (i < lineas) {
                sb.append('\n');
            }
        }
        numerosLinea.setText(sb.toString());
    }

    // ------------------------------------------------------------------
    // Cargar
    // ------------------------------------------------------------------

    private void cargarArchivo() {
        JFileChooser fc = new JFileChooser(archivoActual != null ? archivoActual.getParentFile() : null);
        fc.setFileFilter(new FileNameExtensionFilter("Micro-C (*.c, *.mc, *.txt)", "c", "mc", "txt"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = fc.getSelectedFile();
        try {
            String codigo = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
            quitarResaltado();
            editor.setText(codigo);
            editor.setCaretPosition(0);
            archivoActual = archivo;
            llActual = null;
            jLabel_file.setText(archivo.getName());
            ir.setText("");
            mostrarIROptimizado("Optimización pendiente");
            consola.setText("");
            jButton_compile.setEnabled(true);
            jButton_run.setEnabled(false);
            pestanas.setSelectedIndex(TAB_FUENTE);
        } catch (IOException ex) {
            consola.setText("No se pudo leer el archivo: " + ex.getMessage() + "\n");
            pestanas.setSelectedIndex(TAB_CONSOLA);
        }
    }

    // ------------------------------------------------------------------
    // Compilar
    // ------------------------------------------------------------------

    private void compilar() {
        if (archivoActual == null) {
            return;
        }
        String codigo = editor.getText();
        File archivo = archivoActual;

        quitarResaltado();
        consola.setText("");
        escribirConsola("Compilando...\n");
        pestanas.setSelectedIndex(TAB_CONSOLA);
        jButton_compile.setEnabled(false);
        jButton_run.setEnabled(false);
        llActual = null;

        // Se compila en segundo plano para que la ventana no se congele
        new SwingWorker<String, Void>() {
            private File salida;

            @Override
            protected String doInBackground() throws Exception {
                String codigoIR = generarIR(codigo);
                String base = archivo.getName().replaceFirst("\\.[^.]+$", "");
                salida = new File(archivo.getParentFile(), base + ".ll");
                Files.writeString(salida.toPath(), codigoIR, StandardCharsets.UTF_8);
                return codigoIR;
            }

            @Override
            protected void done() {
                jButton_compile.setEnabled(true);
                try {
                    String codigoIR = get();
                    ir.setText(codigoIR);
                    ir.setCaretPosition(0);
                    llActual = salida;
                    escribirConsola("Compilación exitosa. IR guardado en " + salida.getAbsolutePath() + "\n");
                    jButton_run.setEnabled(proceso == null);
                } catch (Exception ex) {
                    Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
                    mostrarError(causa);
                }
            }
        }.execute();
    }

    /** Corre todas las fases del compilador y devuelve el IR. Lanza la excepción del primer error. */
    private static String generarIR(String codigo) {
        EscuchaErrores escucha = new EscuchaErrores();

        MicroCLexer lexer = new MicroCLexer(CharStreams.fromString(codigo));
        lexer.removeErrorListeners();
        lexer.addErrorListener(escucha);

        MicroCParser parser = new MicroCParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(escucha);

        ParseTree tree = parser.program();
        new CheckLexer().visit(tree);
        Semantico s = new Semantico();
        s.visit(tree);
        return new GeneradorIR(s.getTabla()).generar(tree);
    }

    private void mostrarError(Throwable error) {
        int linea = -1;
        int columna = -1;
        if (error instanceof ErrorCompilacion ec) {
            linea = ec.getLinea();
            columna = ec.getColumna();
        } else if (error instanceof ErrorSemantico es) {
            linea = es.getLinea();
            columna = es.getColumna();
        }

        String mensaje = error.getMessage() != null ? error.getMessage() : error.toString();
        if (!(error instanceof ErrorCompilacion) && !(error instanceof ErrorSemantico)) {
            mensaje = "Error: " + mensaje;
        }
        escribirConsola(mensaje + "\n");

        if (linea > 0) {
            resaltarError(linea, columna);
        }
    }

    // ------------------------------------------------------------------
    // Resaltado de errores
    // ------------------------------------------------------------------

    private static final Highlighter.HighlightPainter PINTOR_ERROR = new Highlighter.HighlightPainter() {
        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r = c.modelToView2D(p0).getBounds();
                g.setColor(ROJO_CLARO);
                g.fillRect(0, r.y, c.getWidth(), r.height);
            } catch (BadLocationException ignored) {
            }
        }
    };

    private void resaltarError(int linea, int columna) {
        try {
            int indice = Math.min(linea, editor.getLineCount()) - 1;
            int inicio = editor.getLineStartOffset(indice);
            int fin = editor.getLineEndOffset(indice);
            resaltado = editor.getHighlighter().addHighlight(inicio, Math.max(inicio, fin), PINTOR_ERROR);

            int col = Math.max(1, columna) - 1;
            int maxCol = Math.max(0, fin - inicio - (indice < editor.getLineCount() - 1 ? 1 : 0));
            editor.setCaretPosition(inicio + Math.min(col, maxCol));
        } catch (BadLocationException ignored) {
        }
        pestanas.setSelectedIndex(TAB_FUENTE);
        editor.requestFocusInWindow();
    }

    private void quitarResaltado() {
        if (resaltado != null) {
            editor.getHighlighter().removeHighlight(resaltado);
            resaltado = null;
        }
    }

    // ------------------------------------------------------------------
    // IR optimizado
    // ------------------------------------------------------------------

    /** Llena la pestaña "IR optimizado". Pensado para llamarse cuando exista el optimizador. */
    public void mostrarIROptimizado(String texto) {
        irOptimizado.setText(texto);
        irOptimizado.setCaretPosition(0);
    }

    // ------------------------------------------------------------------
    // Ejecutar
    // ------------------------------------------------------------------

    private void ejecutar() {
        File ll = llActual;
        if (ll == null || proceso != null) {
            return;
        }
        pestanas.setSelectedIndex(TAB_CONSOLA);

        File runtime = buscarRuntime();
        if (runtime == null) {
            escribirConsola("\nNo se encontró runtime/runtime.c (se buscó en el directorio de trabajo y "
                    + "en las carpetas superiores al archivo cargado).\n");
            return;
        }

        // runtime.ll se genera junto a runtime.c y solo se regenera si runtime.c cambió
        File runtimeLL = new File(runtime.getParentFile(), "runtime.ll");

        jButton_run.setEnabled(false);
        escribirConsola("\n");

        Thread hilo = new Thread(() -> {
            try {
                if (generarRuntimeLL(runtime, runtimeLL)) {
                    correrConLli(ll, runtimeLL);
                }
            } finally {
                SwingUtilities.invokeLater(() -> {
                    entrada.setEnabled(false);
                    jButton_run.setEnabled(llActual != null);
                });
            }
        }, "ejecucion-microc");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Traduce runtime.c a IR con: clang -S -emit-llvm runtime.c -o runtime.ll
     * Devuelve true si runtime.ll quedó listo.
     */
    private boolean generarRuntimeLL(File runtimeC, File runtimeLL) {
        if (runtimeLL.isFile() && runtimeLL.lastModified() >= runtimeC.lastModified()) {
            return true;
        }
        escribirConsola("Generando " + runtimeLL.getName() + " con clang...\n");
        List<String> comando = List.of(
                "clang", "-S", "-emit-llvm",
                "-Wno-format-security", "-Wno-deprecated-declarations",
                runtimeC.getAbsolutePath(), "-o", runtimeLL.getAbsolutePath());
        Process clang;
        try {
            clang = new ProcessBuilder(comando)
                    .directory(runtimeC.getParentFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ex) {
            escribirConsola("No se encontró clang. Instala LLVM y agrégalo al PATH.\n");
            return false;
        }

        try {
            clang.getOutputStream().close();
            String salida = new String(clang.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int codigo = clang.waitFor();
            if (codigo != 0) {
                escribirConsola(salida);
                if (!salida.endsWith("\n")) {
                    escribirConsola("\n");
                }
                escribirConsola("clang falló con código " + codigo + ".\n");
                return false;
            }
            return true;
        } catch (IOException | InterruptedException ex) {
            escribirConsola("Error al ejecutar clang: " + ex.getMessage() + "\n");
            return false;
        }
    }

    /** Ejecuta el IR directamente con: lli --extra-module=runtime.ll programa.ll */
    private void correrConLli(File ll, File runtimeLL) {
        Process p;
        try {
            p = new ProcessBuilder("lli", "--extra-module=" + runtimeLL.getAbsolutePath(), ll.getAbsolutePath())
                    .directory(ll.getParentFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ex) {
            escribirConsola("No se encontró lli. Instala LLVM y agrégalo al PATH.\n");
            return;
        }
        proceso = p;
        escribirConsola("Ejecutando " + ll.getName() + " con lli\n\n");
        SwingUtilities.invokeLater(() -> {
            entrada.setEnabled(true);
            entrada.requestFocusInWindow();
        });

        // Se lee por bloques (no por líneas) para mostrar los mensajes que no terminan en salto de línea
        try (Reader lector = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int n;
            while ((n = lector.read(buffer)) != -1) {
                escribirConsola(new String(buffer, 0, n));
            }
        } catch (IOException ignored) {
            // el flujo se cierra cuando el proceso termina
        }

        try {
            int codigo = p.waitFor();
            escribirConsola("\nEl programa terminó con código de salida " + codigo + "\n");
        } catch (InterruptedException ex) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            proceso = null;
        }
    }

    private void enviarEntrada() {
        Process p = proceso;
        if (p == null) {
            return;
        }
        String linea = entrada.getText();
        entrada.setText("");
        escribirConsola(linea + "\n");
        try {
            OutputStream stdin = p.getOutputStream();
            stdin.write((linea + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException ex) {
            escribirConsola("No se pudo enviar la entrada: " + ex.getMessage() + "\n");
        }
    }

    /** Busca runtime/runtime.c en el directorio de trabajo y en las carpetas superiores al archivo cargado. */
    private File buscarRuntime() {
        List<File> inicios = new ArrayList<>();
        inicios.add(new File(System.getProperty("user.dir")));
        if (archivoActual != null) {
            inicios.add(archivoActual.getAbsoluteFile().getParentFile());
        }
        for (File dir : inicios) {
            for (File d = dir; d != null; d = d.getParentFile()) {
                File candidato = new File(d, "runtime" + File.separator + "runtime.c");
                if (candidato.isFile()) {
                    return candidato;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Agrega texto a la consola desde cualquier hilo. */
    private void escribirConsola(String texto) {
        if (SwingUtilities.isEventDispatchThread()) {
            consola.append(texto);
            consola.setCaretPosition(consola.getDocument().getLength());
        } else {
            SwingUtilities.invokeLater(() -> escribirConsola(texto));
        }
    }

    private static JButton boton(String texto) {
        JButton b = new JButton(texto);
        b.setBackground(Color.WHITE);
        b.setForeground(new Color(102, 102, 102));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        return b;
    }

}
