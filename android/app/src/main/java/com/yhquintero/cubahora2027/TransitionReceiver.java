package com.yhquintero.cubahora2027;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recibe la alarma de una transición y avisa al usuario. */
public class TransitionReceiver extends BroadcastReceiver {

    public static final String ACTION_TRANSITION = "com.yhquintero.cubahora2027.TRANSITION";
    public static final String EXTRA_SOON = "soon";

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean soon = intent != null && intent.getBooleanExtra(EXTRA_SOON, false);
        Notifications.showChange(context, soon);
        // Deja programada la siguiente transición (o ninguna si no hay más).
        ReminderScheduler.scheduleNext(context);
    }
}
