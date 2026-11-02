package com.yhquintero.cubahora2027;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Diagnóstico del cambio de zona horaria.
 *
 * <p>Cuando el cambio no ocurre, lo útil no es un «error» genérico sino saber
 * dónde se rompe la cadena: si no hay canal, si el canal responde pero el
 * sistema no cambia, o si el teléfono ya estaba en la zona correcta. Este
 * informe reúne ese estado y la prueba real, y se puede copiar para
 * compartirlo.</p>
 *
 * <p>{@link #run(Context)} ejecuta comandos y espera: debe llamarse fuera del
 * hilo principal.</p>
 */
public final class Diagnostics {

    /** Informe ya calculado. */
    public static final class Report {
        /** Texto completo, listo para mostrar o copiar. */
        public final String text;
        /** Resultado de la prueba de cambio, o {@code null} si no se intentó. */
        public final AutoSwitch.Outcome outcome;

        Report(String text, AutoSwitch.Outcome outcome) {
            this.text = text;
            this.outcome = outcome;
        }

        public boolean applied() {
            return outcome != null && outcome.applied();
        }
    }

    private Diagnostics() {
    }

    /** Ejecuta el diagnóstico completo. Bloquea: usar fuera del hilo principal. */
    public static Report run(Context context) {
        long now = System.currentTimeMillis();
        int required = CubaRules.offsetMinutes(now);
        String zoneId = OffsetGuide.zoneIdFor(required);

        // Comprobar root si aún no se ha hecho en esta sesión.
        if (!RootChannel.checked()) {
            RootChannel.probe();
        }

        AutoSwitch.Outcome outcome;
        if (AutoSwitch.canApplyNow(context)) {
            outcome = AutoSwitch.applyOffsetDetailed(context, required);
        } else {
            outcome = new AutoSwitch.Outcome(AutoSwitch.Result.NO_CHANNEL, "",
                    AutoSwitch.channelStatus(context));
        }

        String text = compose(context, now, required, zoneId, outcome);
        return new Report(text, outcome);
    }

    private static String compose(Context context, long now, int required,
                                  String zoneId, AutoSwitch.Outcome outcome) {
        StringBuilder text = new StringBuilder();
        text.append("INFORME CUBA HORA 2027\n");
        text.append("Versión de la app: ").append(version(context)).append('\n');
        text.append("Teléfono: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        text.append("Fecha del informe: ").append(format(now)).append('\n');
        text.append('\n');

        text.append("ESTADO\n");
        text.append("Zona que ve la app: ").append(AutoSwitch.appZone()).append('\n');
        text.append("Zona del sistema: ").append(valueOrUnknown(AutoSwitch.readSystemZone()))
                .append('\n');
        text.append("Desfase del teléfono: ")
                .append(CubaRules.labelForMinutes(ZoneController.currentOffsetMinutes(now)))
                .append('\n');
        text.append("Desfase que exige la regla: ")
                .append(CubaRules.labelForMinutes(required)).append('\n');
        text.append("Zona automática de la red: ")
                .append(autoTimeZone(context) ? "activada" : "desactivada").append('\n');
        text.append("Zona objetivo del perfil: ").append(zoneId).append('\n');
        text.append('\n');

        text.append("CANALES\n");
        text.append("Shizuku instalado: ").append(si(ShizukuChannel.installed(context)))
                .append('\n');
        text.append("Shizuku en ejecución: ").append(si(ShizukuChannel.running())).append('\n');
        text.append("Shizuku autorizado: ").append(si(ShizukuChannel.authorized())).append('\n');
        text.append("Root disponible: ").append(si(RootChannel.available()));
        if (RootChannel.variantName() != null) {
            text.append(" (").append(RootChannel.variantName()).append(')');
        }
        text.append('\n');
        text.append('\n');

        text.append("PRUEBA DE CAMBIO\n");
        text.append("Resultado: ").append(label(outcome.result)).append('\n');
        if (outcome.strategy.length() > 0) {
            text.append("Orden que funcionó: ").append(outcome.strategy).append('\n');
        }
        if (outcome.detail.length() > 0) {
            text.append("Detalle: ").append(outcome.detail).append('\n');
        }
        text.append('\n');

        text.append("QUÉ HACER\n");
        text.append(advice(context, outcome, required, zoneId));
        text.append('\n');
        text.append("Comandos equivalentes desde un PC con ADB:\n");
        text.append("  adb shell settings put global auto_time_zone 0\n");
        text.append("  adb shell cmd alarm set-timezone ").append(zoneId).append('\n');
        text.append("  adb shell getprop persist.sys.timezone\n");
        return text.toString();
    }

    private static String advice(Context context, AutoSwitch.Outcome outcome, int required,
                                 String zoneId) {
        String label = CubaRules.labelForMinutes(required);
        switch (outcome.result) {
            case APPLIED:
                return "Listo: el teléfono está en " + label + " y el sistema lo confirma.\n"
                        + "El cambio del 07/11/2027 a la 01:00 se aplicará solo si el canal\n"
                        + "sigue activo ese día ("
                        + (AutoSwitch.canApplyNow(context)
                        ? AutoSwitch.channelName() : "sin canal") + ").";
            case NO_CHANNEL:
                return "No hay ningún canal privilegiado, así que la app no puede cambiar la\n"
                        + "zona. Elige uno:\n"
                        + " 1. Shizuku (sin root): instálalo, activa la depuración inalámbrica\n"
                        + "    y pulsa «Emparejar» → «Iniciar». Vuelve y pulsa «Activar con\n"
                        + "    Shizuku».\n"
                        + " 2. Root: si el teléfono está rooteado, pulsa «Activar con root».\n"
                        + " 3. PC con cable: usa los comandos de abajo.\n"
                        + " 4. Ajustes del teléfono: «Ajustar a mano» y elige "
                        + OffsetGuide.cityFor(required) + ".";
            case UNVERIFIED:
                return "El canal ejecutó la orden, pero el sistema sigue sin confirmar la zona.\n"
                        + "Suele pasar cuando otro ajuste la devuelve: desactiva «Zona horaria\n"
                        + "automática» en Ajustes y vuelve a intentarlo. Si sigue igual, aplica\n"
                        + "los comandos de abajo desde un PC o elige "
                        + OffsetGuide.cityFor(required) + " a mano.";
            default:
                return "El canal respondió con un error al ejecutar el cambio. Copia este\n"
                        + "informe y revísalo: lo habitual es que el gestor de root haya\n"
                        + "denegado el permiso o que Shizuku se haya detenido.\n"
                        + "Mientras tanto puedes usar los comandos de abajo o elegir "
                        + OffsetGuide.cityFor(required) + " a mano.";
        }
    }

    private static String label(AutoSwitch.Result result) {
        switch (result) {
            case APPLIED:
                return "aplicado y verificado";
            case UNVERIFIED:
                return "ejecutado pero sin verificar";
            case NO_CHANNEL:
                return "no se intentó: sin canal";
            default:
                return "falló";
        }
    }

    private static boolean autoTimeZone(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.AUTO_TIME_ZONE) == 1;
        } catch (Throwable error) {
            return false;
        }
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.length() == 0 ? "(sin lectura)" : value;
    }

    private static String si(boolean value) {
        return value ? "sí" : "no";
    }

    private static String format(long epochMillis) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm",
                new Locale("es", "ES"));
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(epochMillis));
    }

    private static String version(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable error) {
            return "?";
        }
    }
}
