package com.yhquintero.cubahora2027;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recibe la alarma de una transición y avisa al usuario. */
public class TransitionReceiver extends BroadcastReceiver {

    public static final String ACTION_TRANSITION = "com.yhquintero.cubahora2027.TRANSITION";
    public static final String EXTRA_DAYS = "days_before";

    @Override
    public void onReceive(Context context, Intent intent) {
        int daysBefore = intent == null ? 0 : intent.getIntExtra(EXTRA_DAYS, 0);
        Notifications.showChange(context, daysBefore);
        // Deja programada la siguiente transición (o ninguna si no hay más).
        ReminderScheduler.scheduleNext(context);
    }
}
