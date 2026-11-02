package com.yhquintero.cubahora2027;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Programa los recordatorios de las transiciones previstas.
 *
 * <p>Las alarmas son meramente informativas: recuerdan al usuario que debe
 * revisar la zona horaria en los ajustes del sistema. La aplicación nunca
 * cambia la zona por sí sola.</p>
 */
public final class ReminderScheduler {

    public static final String PREFS = "cuba_hora_prefs";
    public static final String KEY_REMINDERS = "reminders_enabled";

    private static final String TAG = "CubaHora";
    private static final int REQUEST_WEEK = 4100;
    private static final int REQUEST_SOON = 4102;
    private static final int REQUEST_CHANGE = 4101;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private ReminderScheduler() {
    }

    public static boolean remindersEnabled(Context context) {
        return prefs(context).getBoolean(KEY_REMINDERS, true);
    }

    public static void setRemindersEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_REMINDERS, enabled).apply();
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Recalcula y programa los avisos a partir del instante actual. */
    public static void scheduleNext(Context context) {
        cancel(context);
        if (!remindersEnabled(context)) {
            return;
        }
        long now = System.currentTimeMillis();
        long next = CubaRules.nextTransition(now);
        if (next <= 0) {
            return;
        }
        long week = next - 7 * DAY_MILLIS;
        long soon = next - DAY_MILLIS;
        if (week > now) {
            schedule(context, week, REQUEST_WEEK, 7);
        }
        if (soon > now) {
            schedule(context, soon, REQUEST_SOON, 1);
        }
        if (next > now) {
            schedule(context, next, REQUEST_CHANGE, 0);
        }
    }

    public static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        try {
            alarms.cancel(pending(context, REQUEST_WEEK, 7));
            alarms.cancel(pending(context, REQUEST_SOON, 1));
            alarms.cancel(pending(context, REQUEST_CHANGE, 0));
        } catch (Throwable error) {
            Log.w(TAG, "No se pudieron cancelar los recordatorios", error);
        }
    }

    private static void schedule(Context context, long atMillis, int requestCode, int daysBefore) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        PendingIntent operation = pending(context, requestCode, daysBefore);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (alarms.canScheduleExactAlarms()) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation);
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation);
                }
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation);
            } else {
                alarms.setExact(AlarmManager.RTC_WAKEUP, atMillis, operation);
            }
        } catch (SecurityException error) {
            Log.w(TAG, "Sin permiso para alarmas exactas", error);
            try {
                alarms.set(AlarmManager.RTC_WAKEUP, atMillis, operation);
            } catch (Throwable ignored) {
                // Sin recordatorio: la app sigue avisando al abrirse.
            }
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo programar el recordatorio", error);
        }
    }

    private static PendingIntent pending(Context context, int requestCode, int daysBefore) {
        Intent intent = new Intent(context, TransitionReceiver.class);
        intent.setAction(TransitionReceiver.ACTION_TRANSITION);
        intent.putExtra(TransitionReceiver.EXTRA_DAYS, daysBefore);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
