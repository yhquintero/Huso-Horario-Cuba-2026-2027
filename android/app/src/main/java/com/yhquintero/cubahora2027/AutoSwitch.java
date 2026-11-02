package com.yhquintero.cubahora2027;

import android.content.Context;
import android.util.Log;

/**
 * Cambio automático de la zona horaria en las transiciones de la regla.
 *
 * <p>Coordina tres piezas: la regla ({@link CubaRules}), el canal privilegiado
 * ({@link ShizukuChannel}) y el estado guardado por el usuario. Cuando el
 * usuario activa el cambio automático, la app programa la alarma de la
 * transición y, al dispararse, aplica el desfase nuevo sin intervención.</p>
 *
 * <p>Si el canal no está disponible (Shizuku no instalado, detenido tras un
 * reinicio o sin autorización) no se aplica nada: se muestra el aviso con los
 * pasos manuales. La app nunca informa de un cambio que no ha verificado con
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
        /** Shizuku falló al ejecutar el comando. */
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
    // Disponibilidad del canal
    // ------------------------------------------------------------------

    /** ¿Se puede aplicar ahora sin tocar los ajustes? */
    public static boolean canApplyNow(Context context) {
        return ShizukuChannel.running() && ShizukuChannel.authorized();
    }

    /** Descripción breve del estado del canal privilegiado. */
    public static String channelStatus(Context context) {
        if (!ShizukuChannel.installed(context)) {
            return "Sin canal: instala Shizuku";
        }
        if (!ShizukuChannel.running()) {
            return "Shizuku detenido";
        }
        if (!ShizukuChannel.authorized()) {
            return "Shizuku sin autorizar";
        }
        return "Listo con Shizuku";
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
        if (!ShizukuChannel.running() || !ShizukuChannel.authorized()) {
            return record(context, Result.NO_CHANNEL, channelStatus(context));
        }

        // 1. La zona automática deshace el ajuste en cuanto la red responde.
        ShizukuChannel.exec("settings", "put", "global", "auto_time_zone", "0");

        // 2. Cambio real: el shell tiene SET_TIME_ZONE, la app no.
        ShizukuChannel.Output applied =
                ShizukuChannel.exec("cmd", "alarm", "set-timezone", zoneId);
        if (applied == null) {
            return record(context, Result.FAILED, "Shizuku no respondió");
        }
        if (!applied.ok()) {
            Log.w(TAG, "set-timezone falló: " + applied.describe());
            return record(context, Result.FAILED, applied.describe());
        }

        // 3. Verificación con la fuente de verdad del sistema.
        String active = ShizukuChannel.readSystemZone();
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
