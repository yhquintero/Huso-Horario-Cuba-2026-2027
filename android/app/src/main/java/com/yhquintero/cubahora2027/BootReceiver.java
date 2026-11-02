package com.yhquintero.cubahora2027;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Reagenda los recordatorios y el cambio automático tras un reinicio o una
 * actualización de la app.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Notifications.ensureChannel(context);
            if (AutoSwitch.isEnabled(context)) {
                AutoSwitch.arm(context);
            } else {
                ReminderScheduler.scheduleNext(context);
            }
        } catch (Throwable ignored) {
            // Un fallo aquí nunca debe impedir el arranque del sistema.
        }
    }
}
