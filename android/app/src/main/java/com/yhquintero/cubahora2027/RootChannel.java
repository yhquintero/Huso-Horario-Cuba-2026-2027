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
 * <p>En un teléfono sin root, {@code su} no existe o espera una autorización
 * que nunca llega: por eso la entrada estándar se cierra de inmediato y todo se
 * ejecuta con un límite de tiempo. Así una comprobación fallida tarda segundos
 * y nunca deja un proceso colgado.</p>
 */
public final class RootChannel {

    private static final String TAG = "CubaHora";

    /** Límite por comando: si {@code su} no responde, se abandona. */
    private static final long TIMEOUT_MS = 12000L;

    /**
     * Formas de invocar root. Los gestores no coinciden: Magisk y KernelSU
     * aceptan {@code su -c}; otros usan {@code su 0 -c} o exigen el shell.
     */
    private static final String[][] VARIANTS = {
            {"su", "-c"},
            {"su", "0", "-c"},
            {"su", "root", "-c"},
    };

    /** Último resultado de {@link #probe()}: {@code null} = aún sin comprobar. */
    private static volatile Boolean available;

    /** Variante de {@code su} que funcionó, para no repetir pruebas. */
    private static volatile String[] working;

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
        working = null;
    }

    /** Variante de {@code su} que respondió, o {@code null}. */
    public static String variantName() {
        String[] variant = working;
        if (variant == null) {
            return null;
        }
        StringBuilder name = new StringBuilder();
        for (String part : variant) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(part);
        }
        return name.toString();
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
        String script = join(command);
        String[] preferred = working;
        if (preferred != null) {
            ChannelOutput output = run(preferred, script);
            if (output != null && output.ok()) {
                return output;
            }
        }
        ChannelOutput last = null;
        for (String[] variant : VARIANTS) {
            if (preferred != null && sameVariant(preferred, variant)) {
                continue;
            }
            ChannelOutput output = run(variant, script);
            if (output != null && output.ok()) {
                working = variant;
                return output;
            }
            if (output != null) {
                last = output;
            }
        }
        return last;
    }

    /**
     * Ejecuta un comando ya armado (sin argumentos separados), útil para
     * cadenas con redirecciones.
     */
    public static ChannelOutput shell(String command) {
        if (command == null || command.trim().length() == 0) {
            return null;
        }
        return exec(command.trim());
    }

    private static ChannelOutput run(String[] variant, String script) {
        Process process = null;
        try {
            String[] argv = new String[variant.length + 1];
            System.arraycopy(variant, 0, argv, 0, variant.length);
            argv[variant.length] = script;
            process = Runtime.getRuntime().exec(argv);

            // Sin esto, un su que espera confirmación se queda colgado.
            try {
                process.getOutputStream().close();
            } catch (Throwable ignored) {
                // La entrada ya estaba cerrada.
            }

            final StringBuilder out = new StringBuilder();
            final StringBuilder err = new StringBuilder();
            final InputStream stdout = process.getInputStream();
            final InputStream stderr = process.getErrorStream();
            Thread outReader = new Thread(new Runnable() {
                @Override
                public void run() {
                    drain(stdout, out);
                }
            }, "cuba-su-out");
            Thread errReader = new Thread(new Runnable() {
                @Override
                public void run() {
                    drain(stderr, err);
                }
            }, "cuba-su-err");
            outReader.setDaemon(true);
            errReader.setDaemon(true);
            outReader.start();
            errReader.start();

            int code = waitFor(process, TIMEOUT_MS);
            joinQuietly(outReader);
            joinQuietly(errReader);
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

    private static boolean sameVariant(String[] left, String[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int index = 0; index < left.length; index++) {
            if (!left[index].equals(right[index])) {
                return false;
            }
        }
        return true;
    }

    private static String join(String[] command) {
        StringBuilder script = new StringBuilder();
        for (String part : command) {
            if (script.length() > 0) {
                script.append(' ');
            }
            script.append(quote(part));
        }
        return script.toString();
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

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(1500L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
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
        try {
            process.destroy();
        } catch (Throwable ignored) {
            // Sin proceso que destruir.
        }
        return -1;
    }
}
