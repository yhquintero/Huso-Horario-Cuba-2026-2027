package com.yhquintero.cubahora2027;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import java.util.TimeZone;

/**
 * Lectura del estado horario del teléfono y apertura de los ajustes del
 * sistema.
 *
 * <p>Android no permite que una aplicación instalada por el usuario cambie la
 * zona horaria: {@code AlarmManager.setTimeZone()} exige
 * {@code android.permission.SET_TIME_ZONE}, declarado como
 * {@code signature|privileged|role} en AOSP, y escribir la clave
 * {@code time_zone} en {@code Settings.Global} no la aplica (SettingsProvider
 * no la observa). Por eso esta clase solo lee el estado real y abre la
 * pantalla de ajustes donde el cambio sí es posible.</p>
 */
public final class ZoneController {

    /** Zona de referencia de Cuba en la base de datos IANA. */
    public static final String HAVANA_ZONE_ID = "America/Havana";

    private static final String TAG = "CubaHora";

    private ZoneController() {
    }

    /**
     * Zona horaria efectiva del proceso, que Android mantiene sincronizada con
     * la del sistema (se reinicia al recibir ACTION_TIMEZONE_CHANGED).
     */
    public static String currentZoneId() {
        try {
            String id = TimeZone.getDefault().getID();
            return id == null || id.length() == 0 ? "desconocida" : id;
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo leer la zona horaria", error);
            return "desconocida";
        }
    }

    /** Desfase real del sistema respecto a UTC, en minutos, en un instante dado. */
    public static int currentOffsetMinutes(long epochMillis) {
        try {
            return TimeZone.getDefault().getOffset(epochMillis) / 60000;
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo leer el desfase", error);
            return 0;
        }
    }

    /** Indica si el teléfono ajusta la zona automáticamente (red u operador). */
    public static boolean autoTimeZoneEnabled(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    Settings.Global.AUTO_TIME_ZONE,
                    1) == 1;
        } catch (Throwable error) {
            return true;
        }
    }

    /** Abre la pantalla de fecha y hora del sistema. */
    public static void openDateTimeSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
            return;
        } catch (Throwable ignored) {
            // Algunos fabricantes no exponen esa acción; se cae a Ajustes.
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Throwable ignored) {
            // Sin ajustes disponibles no hay nada más que hacer.
        }
    }
}
