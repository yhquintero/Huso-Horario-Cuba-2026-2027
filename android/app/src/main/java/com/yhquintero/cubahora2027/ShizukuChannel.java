package com.yhquintero.cubahora2027;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

/**
 * Canal privilegiado opcional mediante Shizuku.
 *
 * <p>Android reserva el cambio de zona horaria a las aplicaciones del sistema,
 * pero el usuario puede ceder ese canal a esta app con Shizuku: un servicio que
 * corre con el UID del shell (2000) y que sí tiene
 * {@code android.permission.SET_TIME_ZONE}. Con él, la app puede ejecutar
 * {@code cmd alarm set-timezone} en el momento de la transición y el cambio se
 * aplica solo.</p>
 *
 * <p>Todo el acceso pasa por {@link Shizuku#newProcess}; si Shizuku no está
 * instalado, no está en ejecución o el usuario no ha autorizado la app, cada
 * método devuelve un estado y la app cae al aviso manual sin romper nada.</p>
 */
public final class ShizukuChannel {

    /** Paquete de la aplicación Shizuku (también sirve para Sui). */
    static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    /** Código de petición de permiso que se devuelve en el listener. */
    public static final int REQUEST_CODE = 5001;

    private static final String TAG = "CubaHora";
    private static final long PROCESS_TIMEOUT_MS = 8000L;

    private ShizukuChannel() {
    }

    /** Indica si la aplicación Shizuku está instalada. */
    public static boolean installed(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    /** Indica si el servicio de Shizuku está vivo y conectado. */
    public static boolean running() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable error) {
            return false;
        }
    }

    /** Indica si el usuario ya autorizó esta app en Shizuku. */
    public static boolean authorized() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable error) {
            return false;
        }
    }

    /** Pide la autorización; el resultado llega al listener registrado. */
    public static void requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo pedir permiso a Shizuku", error);
        }
    }

    /**
     * Ejecuta un comando con el UID del shell.
     * Devuelve {@code null} si Shizuku no está disponible o falla la llamada.
     */
    public static ChannelOutput exec(String... command) {
        if (command == null || command.length == 0) {
            return null;
        }
        ShizukuRemoteProcess process = null;
        try {
            process = Shizuku.newProcess(command, null, null);
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
            int code = waitFor(process, PROCESS_TIMEOUT_MS);
            try {
                errorReader.join(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return new ChannelOutput(code, out.toString().trim(),
                    err.toString().trim());
        } catch (Throwable error) {
            Log.w(TAG, "Shizuku no pudo ejecutar el comando", error);
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
