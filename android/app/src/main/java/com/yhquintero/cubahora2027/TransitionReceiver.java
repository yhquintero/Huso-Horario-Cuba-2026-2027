package com.yhquintero.cubahora2027;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Recibe la alarma de una transición y, si el usuario activó el cambio
 * automático, aplica el desfase nuevo con el canal privilegiado.
 *
 * <p>El trabajo se hace en un hilo aparte con {@link #goAsync()}: ejecuta
 * comandos y espera su salida. Al terminar avisa del resultado real (aplicado
 * y verificado, o pendiente) y vuelve a programar la siguiente transición.</p>
 */
public class TransitionReceiver extends BroadcastReceiver {

    public static final String ACTION_TRANSITION = "com.yhquintero.cubahora2027.TRANSITION";
    public static final String EXTRA_DAYS = "days_before";

    private static final String TAG = "CubaHora";

    @Override
    public void onReceive(Context context, Intent intent) {
        int daysBefore = intent == null ? 0 : intent.getIntExtra(EXTRA_DAYS, 0);
        final BroadcastReceiver.PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();

        if (daysBefore > 0) {
            // Avisos previos: solo recordatorio, no se toca la zona.
            Notifications.showChange(appContext, daysBefore);
            ReminderScheduler.scheduleNext(appContext);
            pending.finish();
            return;
        }

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long now = System.currentTimeMillis();
                    if (!AutoSwitch.isEnabled(appContext)) {
                        Notifications.showChange(appContext, 0);
                        return;
                    }
                    AutoSwitch.Result result = AutoSwitch.applyNow(appContext, now);
                    int required = CubaRules.offsetMinutes(now);
                    String label = CubaRules.labelForMinutes(required);
                    if (result == AutoSwitch.Result.APPLIED) {
                        Notifications.showApplied(appContext, true, label,
                                Notifications.appliedNotificationId());
                    } else {
                        Notifications.showApplied(appContext, false,
                                result == AutoSwitch.Result.NO_CHANNEL
                                        ? AutoSwitch.channelStatus(appContext)
                                        : "no verificado",
                                Notifications.appliedNotificationId());
                    }
                } catch (Throwable error) {
                    Log.w(TAG, "Fallo al aplicar la transición", error);
                    Notifications.showChange(appContext, 0);
                } finally {
                    ReminderScheduler.scheduleNext(appContext);
                    pending.finish();
                }
            }
        }, "cuba-transition");
        worker.setDaemon(true);
        worker.start();
    }
}
