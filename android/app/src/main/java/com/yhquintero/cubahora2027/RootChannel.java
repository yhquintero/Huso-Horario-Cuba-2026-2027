package com.yhquintero.cubahora2027;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Canal privilegiado por root ({@code su}).
 *
 * <p>Es la alternativa a Shizuku para teléfonos con root: {@code su} entrega el
 * UID 0, que sí tiene {@code android.permission.SET_TIME_ZONE}, así que los
 * mismos comandos del shell funcionan sin instalar nada más. Requiere que el
 * usuario tenga un gestor de root operativo y autorice la petición la primera
 * vez.</p>
 *
 * <p>En un teléfono sin root, {@code su} no existe y la comprobación falla en
 * milisegundos sin afectar a la app: se cae al aviso manual. La comprobación se
 * guarda en memoria ({@link #available()}) porque los canales se consultan
 * desde el hilo de interfaz.</p>
 */
public final class RootChannel {

    private static final String TAG = "CubaHora";
    private static final long TIMEOUT_MS = 6000L;

    /** Último resultado de {@link #probe()}: {@code null} = aún sin comprobar. */
    private static volatile Boolean available;

    private RootChannel() {
    }

    /** ¿Hay root comprobado? No ejecuta nada: usa el último resultado. */
    public static boolean available() {
        return Boolean.TRUE.equals(available);
    }

    /** ¿Se ha intentado comprobar root en esta sesión? */
    public static boolean checked() {
        return available != null;
    }

    /** Olvida el resultado para volver a comprobarlo (p. ej. tras instalar root). */
    public static void forget() {
        available = null;
    }

    /**
     * Comprueba si {@code su} responde y entrega el UID 0.
     * Bloquea: debe llamarse fuera del hilo principal.
     */
    public static boolean probe() {
        ChannelOutput output = exec("id");
        boolean root = output != null && output.ok() && output.out.contains("uid=0");
        available = Boolean.valueOf(root);
        return root;
    }

    /**
     * Ejecuta un comando como root.
     * Devuelve {@code null} si {@code su} no está disponible o falla la llamada.
     */
    public static ChannelOutput exec(String... command) {
        if (command == null || command.length == 0) {
            return null;
        }
        StringBuilder script = new StringBuilder();
        for (String part : command) {
            if (script.length() > 0) {
                script.append(' ');
            }
            script.append(quote(part));
        }
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", script.toString()});
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();

            final InputStream errorStream = process.getErrorStream();
            final StringBuilder errorBuilder = err;
            Thread errorReader = new Thread(new Runnable() {
                @Override
                public void run() {
                    drain(errorStream, errorBuilder);
                }
            });
            errorReader.setDaemon(true);
            errorReader.start();

            drain(process.getInputStream(), out);
            int code = waitFor(process, TIMEOUT_MS);
            try {
                errorReader.join(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return new ChannelOutput(code, out.toString().trim(), err.toString().trim());
        } catch (Throwable error) {
            Log.w(TAG, "su no está disponible en este teléfono", error);
            return null;
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                    // El proceso ya terminó.
                }
            }
        }
    }

    /** Entrecomilla un argumento para el shell remoto de {@code su -c}. */
    private static String quote(String part) {
        if (part != null && part.matches("[A-Za-z0-9_./:=+-]+")) {
            return part;
        }
        String value = part == null ? "" : part;
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void drain(InputStream stream, StringBuilder target) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                target.append(line).append('\n');
            }
        } catch (Throwable ignored) {
            // Lectura interrumpida: se devuelve lo leído hasta ahora.
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                    // Nada que liberar.
                }
            }
        }
    }

    private static int waitFor(Process process, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                return process.exitValue();
            } catch (IllegalThreadStateException running) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            } catch (Throwable error) {
                return -1;
            }
        }
        return -1;
    }
}
