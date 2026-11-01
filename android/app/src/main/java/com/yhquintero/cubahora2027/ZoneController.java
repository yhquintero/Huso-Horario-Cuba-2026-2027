package com.yhquintero.cubahora2027;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import java.util.TimeZone;

/**
 * Lectura y escritura de la zona horaria del sistema.
 *
 * <p>Android no permite a una aplicación normal cambiar la zona horaria.
 * La ruta admitida aquí es {@code WRITE_SECURE_SETTINGS}, un permiso de nivel
 * "desarrollo" que el usuario puede conceder con ADB. Sin ese permiso la app
 * solo lee el estado y guía al usuario a los ajustes del sistema.</p>
 */
public final class ZoneController {

    public static final int RESULT_OK = 0;
    public static final int RESULT_NO_PERMISSION = 1;
    public static final int RESULT_FAILED = 2;

    /** Zona de referencia de Cuba en la base de datos IANA. */
    public static final String HAVANA_ZONE_ID = "America/Havana";

    private static final String TAG = "CubaHora";

    private ZoneController() {
    }

    public static String currentZoneId(Context context) {
        try {
            String id = Settings.Global.getString(context.getContentResolver(), Settings.Global.TIME_ZONE);
            if (id != null && id.length() > 0) {
                return id;
            }
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo leer Settings.Global.TIME_ZONE", error);
        }
        return TimeZone.getDefault().getID();
    }

    public static int currentOffsetMinutes(Context context, long epochMillis) {
        String id = currentZoneId(context);
        TimeZone zone = TimeZone.getTimeZone(id);
        return zone.getOffset(epochMillis) / 60000;
    }

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

    public static boolean canWriteSecureSettings(Context context) {
        try {
            return context.getPackageManager().checkPermission(
                    Manifest.permission.WRITE_SECURE_SETTINGS,
                    context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable error) {
            return false;
        }
    }

    /**
     * Fija la zona indicada y desactiva el ajuste automático.
     * Devuelve uno de los códigos {@code RESULT_*}.
     */
    public static int applyZone(Context context, String zoneId) {
        if (canWriteSecureSettings(context)) {
            ContentResolver resolver = context.getContentResolver();
            try {
                // Desactivar primero el automático evita que la red lo sobrescriba.
                Settings.Global.putString(resolver, Settings.Global.AUTO_TIME_ZONE, "0");
                Settings.Global.putString(resolver, Settings.Global.TIME_ZONE, zoneId);
            } catch (SecurityException error) {
                Log.w(TAG, "Sin permiso para escribir la zona", error);
                return RESULT_NO_PERMISSION;
            } catch (Throwable error) {
                Log.w(TAG, "Error al escribir la zona", error);
                return RESULT_FAILED;
            }

            String written = null;
            try {
                written = Settings.Global.getString(resolver, Settings.Global.TIME_ZONE);
            } catch (Throwable ignored) {
                // Se valida igualmente con el reloj efectivo.
            }

            TimeZone.setDefault(TimeZone.getTimeZone(zoneId));

            if (written != null && zoneId.equals(written)) {
                return RESULT_OK;
            }
            return zoneId.equals(TimeZone.getDefault().getID()) ? RESULT_OK : RESULT_FAILED;
        }

        // Alternativa para dispositivos con SET_TIME_ZONE concedido por el fabricante.
        try {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) {
                alarms.setTimeZone(zoneId);
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
                return RESULT_OK;
            }
        } catch (Throwable error) {
            Log.w(TAG, "setTimeZone no disponible", error);
        }

        return RESULT_NO_PERMISSION;
    }

    /** Vuelve a la zona de Cuba con ajuste automático del sistema. */
    public static int restoreAutomatic(Context context) {
        if (!canWriteSecureSettings(context)) {
            return RESULT_NO_PERMISSION;
        }
        ContentResolver resolver = context.getContentResolver();
        try {
            Settings.Global.putString(resolver, Settings.Global.TIME_ZONE, HAVANA_ZONE_ID);
            Settings.Global.putString(resolver, Settings.Global.AUTO_TIME_ZONE, "1");
            TimeZone.setDefault(TimeZone.getTimeZone(HAVANA_ZONE_ID));
            return RESULT_OK;
        } catch (SecurityException error) {
            return RESULT_NO_PERMISSION;
        } catch (Throwable error) {
            Log.w(TAG, "Error al restaurar el automático", error);
            return RESULT_FAILED;
        }
    }

    /** Abre la pantalla de fecha y hora del sistema. */
    public static void openDateTimeSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_DATE_SETTINGS);
            activity.startActivity(intent);
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
