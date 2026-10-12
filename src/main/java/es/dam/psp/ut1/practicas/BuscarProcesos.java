package es.dam.psp.ut1.practicas;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * BuscarProcesos.java · Práctica 2.c · "ps" casero filtrado.
 * Uso: pasa una cadena como argumento (Run > Edit Configurations > Program arguments), p. ej. java.
 * Muestra solo los procesos cuyo comando contiene la cadena: PID, PPID, usuario,
 * hora de inicio y tiempo total de CPU.
 */
public class BuscarProcesos {

    private static final String FORMATO_FILA = "%-8s %-8s %-14s %-19s %-10s %s%n";
    private static final String SIN_DATO = "?";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Uso: BuscarProcesos <cadena>   (ejemplo: java)");
            return;
        }
        String buscada = args[0].toLowerCase();
        DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());

        System.out.printf(FORMATO_FILA, "PID", "PPID", "USUARIO", "INICIO", "CPU", "COMANDO");
        ProcessHandle.allProcesses()
                .filter(p -> p.info().command().map(c -> c.toLowerCase().contains(buscada)).orElse(false))
                .sorted(Comparator.comparingLong(ProcessHandle::pid))
                .forEach(p -> {
                    ProcessHandle.Info info = p.info();
                    System.out.printf(FORMATO_FILA,
                            p.pid(),
                            p.parent().map(padre -> String.valueOf(padre.pid())).orElse("-"),
                            ultimos(info.user().orElse(SIN_DATO), 14),
                            info.startInstant().map(formatoHora::format).orElse(SIN_DATO),
                            info.totalCpuDuration().map(d -> String.format("%.2f s", d.toMillis() / 1000.0)).orElse(SIN_DATO),
                            ultimos(info.command().get(), 60));
                });
    }

    /** Los n últimos caracteres de s (equivale a takeLast de Kotlin). */
    private static String ultimos(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }
}
