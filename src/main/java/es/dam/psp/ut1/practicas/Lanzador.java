import es.dam.psp.ut1.Jvm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Lanzador.java · Práctica 3 · Lanzador de órdenes.
 *
 * Bucle: pide una orden, la ejecuta como proceso hijo con la shell del sistema y muestra
 * su salida numerada (3.a), su código y su duración (3.b). Si tarda más de TIMEOUT_S segundos
 * se termina el proceso y sus descendientes y el código es -1 (3.c). Cada ejecución se añade
 * a historial.txt con el formato orden;codigo;milisegundos (3.d). "salir" termina el programa.
 */

    static final long TIMEOUT_S = 5;   // segundos que se deja trabajar a cada orden
    private static final int CODIGO_TIMEOUT = -1;
    private static final long NANOS_POR_MS = 1_000_000L;
    private static final File HISTORIAL = new File("historial.txt");

    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Lanzador PSP · sistema: " + System.getProperty("os.name") + " · escribe 'salir' para terminar");
        while (true) {
            System.out.print("> ");
            String orden = teclado.readLine();          // null = fin de la entrada (Ctrl+D)
            if (orden == null) break;
            orden = orden.trim();
            if (orden.equals("salir")) break;
            if (orden.isEmpty()) continue;
            ejecutar(orden);
        }
        System.out.println("Hasta luego");
    }

    /** Ejecuta la orden como proceso hijo y muestra su salida, su código y su duración. */
    private static void ejecutar(String orden) throws IOException, InterruptedException {
        File salida = File.createTempFile("lanzador", ".txt");   // 3.c: el padre no lee del pipe
        try {
            Process hijo = new ProcessBuilder(Jvm.comandoShell(orden))
                    .redirectErrorStream(true)                    // 3.a: stdout y stderr juntas
                    .redirectOutput(salida)
                    .start();
            long inicio = System.nanoTime();                      // 3.b: tiempo desde start()
            hijo.getOutputStream().close();                       // sin entrada: evita esperas por stdin

            int codigo;
            if (hijo.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
                codigo = hijo.exitValue();
            } else {
                System.out.println("⚠ La orden tarda más de " + TIMEOUT_S + " s: se termina el proceso.");
                terminar(hijo);
                codigo = CODIGO_TIMEOUT;
            }
            long ms = (System.nanoTime() - inicio) / NANOS_POR_MS;

            List<String> lineas = new String(Files.readAllBytes(salida.toPath())).lines().toList();
            for (int i = 0; i < lineas.size(); i++) {             // 3.a
                System.out.printf("%3d | %s%n", i + 1, lineas.get(i));
            }
            System.out.println("[código " + codigo + " · " + ms + " ms]");                      // 3.b
            Files.writeString(HISTORIAL.toPath(), orden + ";" + codigo + ";" + ms + "\n",       // 3.d
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } finally {
            salida.delete();
        }
    }

    /** 3.c: termina a los descendientes y al hijo; si resisten, destroyForcibly(). */
    private static void terminar(Process hijo) throws InterruptedException {
        List<ProcessHandle> descendientes = hijo.descendants().toList();   // antes de matar al padre
        descendientes.forEach(ProcessHandle::destroy);
        hijo.destroy();
        if (!hijo.waitFor(1, TimeUnit.SECONDS)) {
            descendientes.forEach(ProcessHandle::destroyForcibly);
            hijo.destroyForcibly();
            hijo.waitFor();
        }
    }

