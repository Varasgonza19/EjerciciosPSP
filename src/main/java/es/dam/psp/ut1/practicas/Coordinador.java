package es.dam.psp.ut1.practicas;

import es.dam.psp.ut1.Jvm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Coordinador.java · Práctica 4 · proceso PADRE. Completa los TODO 4.a … 4.e (letras de la práctica).
 *
 * Apuntes UT-1, apartados 5.2 (Jvm.proceso), 5.1 (redirectInput), 6 (leer antes de esperar)
 * y 6.3 (lanzar todos y después esperar a todos).
 * Antes: ejecuta practicas/GeneradorDatos.java para crear datos/ en la raíz del proyecto.
 * Cómo ejecutarlo: triángulo ▶ junto a main, sin argumentos. Cada hijo es practicas/Contador.java.
 *
 * Reparte los ficheros de datos/ entre procesos hijo Contador, recoge sus resultados
 * y muestra el total. Compara el tiempo en modo secuencial y en modo concurrente.
 * Mejora opcional: añade concurrenteLimitado(ficheros, maximo) y mídelo con 1, 2, 4 y 8.
 */
public class Coordinador {

    /**
     * Resultado que devuelve un hijo para un fichero.
     * Un record (Java 16+) es como una data class de Kotlin: constructor, getters
     * (r.lineas(), r.palabras()...), equals, hashCode y toString automáticos.
     */
    record Resultado(String fichero, long lineas, long palabras, long caracteres, String masFrecuente) { }

    static final String CLASE_HIJO = "es.dam.psp.ut1.practicas.Contador";   // main de Contador.java

    /**
     * Crea y ARRANCA un proceso Contador cuya entrada estándar sea el fichero.
     * Pista: Jvm.proceso(...) y redirectInput(...). Hereda la salida de error del hijo.
     */
    static Process lanzarContador(File fichero) throws IOException {
        return Jvm.proceso(CLASE_HIJO)
                .redirectInput(fichero)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
    }

    /**
     * Lee la línea que ha escrito el hijo, espera a que termine y construye el Resultado.
     * Si el código de salida no es 0, lanza una IllegalStateException con un mensaje claro.
     */
    static Resultado recogerResultado(File fichero, Process hijo) throws IOException, InterruptedException {
        String linea;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(hijo.getInputStream()))) {
            linea = r.readLine();                       // se lee ANTES de esperar
        }
        int codigo = hijo.waitFor();
        if (codigo != 0) {
            throw new IllegalStateException("El contador de '" + fichero.getName()
                    + "' terminó con código de salida " + codigo);
        }
        if (linea == null) {
            throw new IllegalStateException("El contador de '" + fichero.getName() + "' no escribió ninguna línea");
        }
        String[] p = linea.split(";", 4);
        if (p.length != 4) {
            throw new IllegalStateException("Línea con formato inesperado para '" + fichero.getName() + "': " + linea);
        }
        return new Resultado(fichero.getName(), Long.parseLong(p[0]), Long.parseLong(p[1]),
                Long.parseLong(p[2]), p[3]);
    }

    /** Procesa los ficheros uno detrás de otro: lanzar, esperar, lanzar, esperar... */
    static List<Resultado> secuencial(List<File> ficheros) throws IOException, InterruptedException {
        List<Resultado> resultados = new ArrayList<>();
        for (File fichero : ficheros) {
            resultados.add(recogerResultado(fichero, lanzarContador(fichero)));
        }
        return resultados;
    }

    /** Lanza TODOS los hijos a la vez y después recoge los resultados. */
    static List<Resultado> concurrente(List<File> ficheros) throws IOException, InterruptedException {
        List<Process> hijos = new ArrayList<>();
        for (File fichero : ficheros) {
            hijos.add(lanzarContador(fichero));         // 1. lanzar TODOS
        }
        List<Resultado> resultados = new ArrayList<>();
        for (int i = 0; i < ficheros.size(); i++) {     // 2. recoger después
            resultados.add(recogerResultado(ficheros.get(i), hijos.get(i)));
        }
        return resultados;
    }

    /** Mejora opcional: nunca más de "maximo" hijos vivos a la vez. */
    static List<Resultado> concurrenteLimitado(List<File> ficheros, int maximo)
            throws IOException, InterruptedException {
        if (maximo < 1) throw new IllegalArgumentException("maximo debe ser >= 1");
        Resultado[] resultados = new Resultado[ficheros.size()];
        ArrayDeque<Integer> enCurso = new ArrayDeque<>();    // índices de los ficheros en marcha
        Process[] hijos = new Process[ficheros.size()];
        for (int i = 0; i < ficheros.size(); i++) {
            if (enCurso.size() >= maximo) {
                int j = enCurso.removeFirst();               // se recoge el más antiguo
                resultados[j] = recogerResultado(ficheros.get(j), hijos[j]);
            }
            hijos[i] = lanzarContador(ficheros.get(i));
            enCurso.addLast(i);
        }
        while (!enCurso.isEmpty()) {
            int j = enCurso.removeFirst();
            resultados[j] = recogerResultado(ficheros.get(j), hijos[j]);
        }
        return Arrays.asList(resultados);
    }

    /** 4.e: tabla con una fila por fichero y una fila TOTAL. */
    private static void mostrarTabla(List<Resultado> resultados) {
        String formato = "%-14s %10s %12s %14s  %s%n";
        System.out.printf(formato, "FICHERO", "LÍNEAS", "PALABRAS", "CARACTERES", "MÁS FRECUENTE");
        for (Resultado r : resultados) {
            System.out.printf(formato, r.fichero(), r.lineas(), r.palabras(), r.caracteres(), r.masFrecuente());
        }
        System.out.printf(formato, "TOTAL",
                resultados.stream().mapToLong(Resultado::lineas).sum(),
                resultados.stream().mapToLong(Resultado::palabras).sum(),
                resultados.stream().mapToLong(Resultado::caracteres).sum(), "-");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Ruta relativa: datos/ dentro del directorio de trabajo (la raíz del proyecto en IntelliJ)
        File[] lista = new File("datos").listFiles(f -> f.getName().endsWith(".txt"));
        if (lista == null || lista.length == 0) {
            System.out.println("No hay ficheros en datos/. Ejecuta antes GeneradorDatos.java");
            return;
        }
        List<File> ficheros = Arrays.stream(lista).sorted(Comparator.comparing(File::getName)).toList();
        System.out.println("Procesadores disponibles: " + Runtime.getRuntime().availableProcessors());

        long inicio = System.nanoTime();
        List<Resultado> r1 = secuencial(ficheros);
        long t1 = System.nanoTime() - inicio;

        inicio = System.nanoTime();
        List<Resultado> r2 = concurrente(ficheros);
        long t2 = System.nanoTime() - inicio;

        mostrarTabla(r2);
        System.out.println("¿Secuencial y concurrente dan el mismo resultado? " + r1.equals(r2));

        System.out.printf("Secuencial:  %.3f s%n", t1 / 1e9);
        System.out.printf("Concurrente: %.3f s%n", t2 / 1e9);
        System.out.printf("Aceleración: %.2f%n", (double) t1 / t2);   // 4.f: t1 / t2


    }
}
