package com.yhquintero.cubahora2027;

import android.content.Context;
import android.util.Log;

/**
 * Cambio automático de la zona horaria en las transiciones de la regla.
 *
 * <p>Coordina la regla ({@link CubaRules}), los canales privilegiados
 * ({@link ShizukuChannel} y {@link RootChannel}) y el estado guardado por el
 * usuario. Cuando el usuario instala el perfil Cuba 2026-2027, la app programa
 * la alarma de la transición y, al dispararse, aplica el desfase nuevo sin
 * intervención.</p>
 *
 * <p>Se usa el primer canal disponible: Shizuku si está autorizado y, si no,
 * root. Sin ninguno de los dos no se aplica nada: la app avisa con los pasos
 * manuales y nunca informa de un cambio que no ha verificado leyendo
 * {@code getprop persist.sys.timezone}.</p>
 */
public final class AutoSwitch {

    /** Preferencias del cambio automático. */
    public static final String PREFS = "cuba_hora_auto";
    public static final String KEY_ENABLED = "auto_enabled";
    public static final String KEY_LAST_RESULT = "last_result";

    private static final String TAG = "CubaHora";

    /** Resultado de intentar aplicar la regla. */
    public enum Result {
        /** Cambio aplicado y verificado en el sistema. */
        APPLIED,
        /** El sistema aceptó la orden pero la lectura posterior no coincide. */
        UNVERIFIED,
        /** No hay ningún canal privilegiado disponible. */
        NO_CHANNEL,
        /** El canal falló al ejecutar el comando. */
        FAILED
    }

    private AutoSwitch() {
    }

    // ------------------------------------------------------------------
    // Estado del usuario
    // ------------------------------------------------------------------

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String lastResult(Context context) {
        return prefs(context).getString(KEY_LAST_RESULT, "");
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    // Canales disponibles
    // ------------------------------------------------------------------

    /** Shizuku instalado, vivo y autorizado para esta app. */
    public static boolean shizukuReady() {
        try {
            return ShizukuChannel.running() && ShizukuChannel.authorized();
        } catch (Throwable error) {
            return false;
        }
    }

    /** ¿Se puede aplicar ahora sin tocar los ajustes? (Shizuku o root). */
    public static boolean canApplyNow(Context context) {
        return shizukuReady() || RootChannel.available();
    }

    /** Nombre corto del canal que se usaría ahora mismo, o cadena vacía. */
    public static String channelName() {
        if (shizukuReady()) {
            return "Shizuku";
        }
        if (RootChannel.available()) {
            return "root";
        }
        return "";
    }

    /** Descripción breve y concreta del estado de los canales. */
    public static String channelStatus(Context context) {
        if (shizukuReady()) {
            return "Activo con Shizuku";
        }
        if (RootChannel.available()) {
            return "Activo con root";
        }
        if (!ShizukuChannel.installed(context)) {
            return RootChannel.checked()
                    ? "Sin canal: ni Shizuku ni root"
                    : "Comprobando Shizuku y root…";
        }
        if (!ShizukuChannel.running()) {
            return "Shizuku detenido · ábrelo o usa root";
        }
        if (!ShizukuChannel.authorized()) {
            return "Shizuku instalado, sin autorizar";
        }
        return "Sin canal disponible";
    }

    /**
     * Ejecuta un comando con el mejor canal disponible: Shizuku y, si falla,
     * root. Devuelve {@code null} si no hay ninguno.
     */
    static ChannelOutput exec(String... command) {
        if (shizukuReady()) {
            ChannelOutput output = ShizukuChannel.exec(command);
            if (output != null && output.ok()) {
                return output;
            }
            ChannelOutput root = RootChannel.exec(command);
            if (root != null) {
                return root;
            }
            return output;
        }
        return RootChannel.exec(command);
    }

    /**
     * Zona activa según el propio sistema: la fuente de verdad para verificar.
     * Se lee con privilegios cuando hay canal y, si no, con {@code getprop}
     * directo, que no necesita permisos.
     */
    public static String readSystemZone() {
        ChannelOutput output = exec("getprop", "persist.sys.timezone");
        if (output != null && output.ok()) {
            String value = output.out.trim();
            if (value.length() > 0) {
                return value;
            }
        }
        return readZoneWithoutChannel();
    }

    /** Lectura de respaldo sin canal: {@code getprop} no requiere privilegios. */
    private static String readZoneWithoutChannel() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"getprop", "persist.sys.timezone"});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), "UTF-8"));
            String value = reader.readLine();
            reader.close();
            process.waitFor();
            return value == null || value.trim().length() == 0 ? null : value.trim();
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo leer persist.sys.timezone", error);
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

    // ------------------------------------------------------------------
    // Aplicación
    // ------------------------------------------------------------------

    /**
     * Aplica el desfase que exige la regla en este instante.
     *
     * <p>Debe llamarse fuera del hilo principal: ejecuta comandos y espera su
     * salida. Devuelve el resultado y deja constancia en las preferencias.</p>
     */
    public static Result applyNow(Context context, long epochMillis) {
        int required = CubaRules.offsetMinutes(epochMillis);
        return applyOffset(context, required);
    }

    /** Aplica un desfase concreto (en minutos respecto a UTC). */
    public static Result applyOffset(Context context, int offsetMinutes) {
        String zoneId = OffsetGuide.zoneIdFor(offsetMinutes);
        if (zoneId == null) {
            return record(context, Result.FAILED, "desfase sin zona fija asociada");
        }
        if (!canApplyNow(context)) {
            return record(context, Result.NO_CHANNEL, channelStatus(context));
        }

        // 1. La zona automática deshace el ajuste en cuanto la red responde.
        exec("settings", "put", "global", "auto_time_zone", "0");

        // 2. Cambio real: el shell (o root) tiene SET_TIME_ZONE, la app no.
        ChannelOutput applied = exec("cmd", "alarm", "set-timezone", zoneId);
        if (applied == null) {
            return record(context, Result.FAILED, "ningún canal respondió");
        }
        if (!applied.ok()) {
            Log.w(TAG, "set-timezone falló: " + applied.describe());
            return record(context, Result.FAILED, applied.describe());
        }

        // 3. Verificación con la fuente de verdad del sistema.
        String active = readSystemZone();
        if (zoneId.equals(active)) {
            return record(context, Result.APPLIED, zoneId);
        }
        Log.w(TAG, "zona leída tras aplicar: " + active);
        return record(context, Result.UNVERIFIED, String.valueOf(active));
    }

    private static Result record(Context context, Result result, String detail) {
        prefs(context).edit()
                .putString(KEY_LAST_RESULT, result.name() + ":" + detail)
                .apply();
        return result;
    }

    // ------------------------------------------------------------------
    // Programación
    // ------------------------------------------------------------------

    /**
     * Arma las alarmas de la transición y del aviso previo.
     * Es idempotente: se puede llamar al activar, al reiniciar y al abrir.
     */
    public static void arm(Context context) {
        ReminderScheduler.scheduleNext(context);
    }

    /** Desactiva el cambio automático y retira las alarmas. */
    public static void disarm(Context context) {
        setEnabled(context, false);
        ReminderScheduler.cancel(context);
    }

    /** Texto para la interfaz sobre la próxima aplicación automática. */
    public static String nextApplicationLabel(Context context) {
        if (!isEnabled(context)) {
            return "Desactivado";
        }
        long now = System.currentTimeMillis();
        long next = CubaRules.nextTransition(now);
        if (next <= 0) {
            return "Sin transiciones pendientes";
        }
        return CubaRules.labelForMinutes(CubaRules.offsetMinutes(next)) + " el "
                + java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(new java.util.Date(next))
                + (canApplyNow(context) ? " (automático)" : " (solo aviso)");
    }
}
