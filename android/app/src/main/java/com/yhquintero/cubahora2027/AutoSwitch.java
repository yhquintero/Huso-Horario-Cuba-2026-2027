package com.yhquintero.cubahora2027;

import android.content.Context;
import android.util.Log;

import java.util.TimeZone;

/**
 * Cambio automático de la zona horaria en las transiciones de la regla.
 *
 * <p>Coordina la regla ({@link CubaRules}), los canales privilegiados
 * ({@link ShizukuChannel} y {@link RootChannel}) y el estado guardado por el
 * usuario. Cuando el usuario instala el perfil Cuba 2026-2027, la app programa
 * la alarma de la transición y, al dispararse, aplica el desfase nuevo sin
 * intervención.</p>
 *
 * <p>El cambio se intenta con varias órdenes de reserva y, después de cada una,
 * se lee la zona del sistema. Solo se informa de «aplicado» cuando la lectura
 * coincide: la app nunca supone que el cambio ocurrió.</p>
 */
public final class AutoSwitch {

    /** Preferencias del cambio automático. */
    public static final String PREFS = "cuba_hora_auto";
    public static final String KEY_ENABLED = "auto_enabled";
    public static final String KEY_LAST_RESULT = "last_result";

    private static final String TAG = "CubaHora";

    /** Intentos de lectura de la zona tras aplicar (la red puede tardar). */
    private static final int VERIFY_ATTEMPTS = 10;
    private static final long VERIFY_PAUSE_MS = 250L;

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

    /** Resultado con el detalle de qué orden funcionó. */
    public static final class Outcome {
        public final Result result;
        /** Orden que verificó el cambio, o la que se intentó al final. */
        public final String strategy;
        /** Zona leída del sistema tras aplicar, o el error del canal. */
        public final String detail;

        Outcome(Result result, String strategy, String detail) {
            this.result = result;
            this.strategy = strategy == null ? "" : strategy;
            this.detail = detail == null ? "" : detail;
        }

        public boolean applied() {
            return result == Result.APPLIED;
        }
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

    /** Zona que ve esta app, leída por el framework (sin privilegios). */
    public static String appZone() {
        try {
            return TimeZone.getDefault().getID();
        } catch (Throwable error) {
            return "?";
        }
    }

    /**
     * ¿El teléfono ya está en la zona que exige la regla? Se comprueba por el
     * desfase efectivo, no por el nombre: cualquier zona con el mismo desfase
     * fijo sirve.
     */
    public static boolean inSync(Context context, long epochMillis) {
        return ZoneController.currentOffsetMinutes(epochMillis)
                == CubaRules.offsetMinutes(epochMillis);
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
        return applyOffset(context, CubaRules.offsetMinutes(epochMillis));
    }

    /** Igual que {@link #applyNow}, con el detalle de la orden que funcionó. */
    public static Outcome applyNowDetailed(Context context, long epochMillis) {
        return applyOffsetDetailed(context, CubaRules.offsetMinutes(epochMillis));
    }

    /** Aplica un desfase concreto (en minutos respecto a UTC). */
    public static Result applyOffset(Context context, int offsetMinutes) {
        return applyOffsetDetailed(context, offsetMinutes).result;
    }

    /**
     * Aplica el desfase probando varias órdenes del sistema, de la más limpia a
     * la más directa, y verificando después de cada una.
     */
    public static Outcome applyOffsetDetailed(Context context, int offsetMinutes) {
        String zoneId = OffsetGuide.zoneIdFor(offsetMinutes);
        if (zoneId == null) {
            return record(context, Result.FAILED, "",
                    "desfase sin zona fija asociada");
        }
        if (!canApplyNow(context)) {
            return record(context, Result.NO_CHANNEL, "", channelStatus(context));
        }

        // 0. Que la red no deshaga el ajuste en cuanto responda.
        exec("settings", "put", "global", "auto_time_zone", "0");

        // Las órdenes se prueban en orden; la primera que se verifique gana.
        String[][] strategies = {
                {"cmd", "alarm", "set-timezone", zoneId},
                {"setprop", "persist.sys.timezone", zoneId},
                {"settings", "put", "global", "time_zone", zoneId},
        };
        String[] names = {
                "cmd alarm set-timezone",
                "setprop persist.sys.timezone",
                "settings put global time_zone",
        };

        String lastError = "";
        for (int index = 0; index < strategies.length; index++) {
            ChannelOutput output = exec(strategies[index]);
            if (output == null) {
                lastError = "el canal no respondió";
                continue;
            }
            if (!output.ok()) {
                lastError = names[index] + ": " + output.describe();
                Log.w(TAG, lastError);
            }
            String active = verify(zoneId);
            if (matches(zoneId, active)) {
                return record(context, Result.APPLIED, names[index], active);
            }
            lastError = names[index] + " no cambió la zona (leída: "
                    + (active == null ? "sin lectura" : active) + ")";
            Log.w(TAG, lastError);
        }

        // Última comprobación antes de rendirse.
        String active = readSystemZone();
        if (matches(zoneId, active)) {
            return record(context, Result.APPLIED, "verificación final", active);
        }
        if (active != null && active.length() > 0) {
            return record(context, Result.UNVERIFIED, "", active);
        }
        return record(context, Result.FAILED, "", lastError);
    }

    /** Reintenta la lectura unos instantes: el sistema aplica sin prisa. */
    private static String verify(String zoneId) {
        String active = readSystemZone();
        for (int attempt = 0; attempt < VERIFY_ATTEMPTS; attempt++) {
            if (matches(zoneId, active)) {
                return active;
            }
            try {
                Thread.sleep(VERIFY_PAUSE_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            active = readSystemZone();
        }
        return active;
    }

    /** La zona pedida puede ser la del sistema o la que ve el framework. */
    private static boolean matches(String zoneId, String active) {
        return zoneId.equals(active) || zoneId.equals(appZone());
    }

    private static Outcome record(Context context, Result result, String strategy,
                                  String detail) {
        prefs(context).edit()
                .putString(KEY_LAST_RESULT, result.name() + ":" + detail)
                .apply();
        return new Outcome(result, strategy, detail);
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
