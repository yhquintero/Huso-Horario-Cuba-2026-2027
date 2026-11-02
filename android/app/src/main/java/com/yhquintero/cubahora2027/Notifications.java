package com.yhquintero.cubahora2027;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/** Notificaciones locales de la aplicación (sin servicios remotos). */
public final class Notifications {

    public static final String CHANNEL_ID = "cambios_hora";
    private static final String TAG = "CubaHora";
    private static final int ID_WEEK = 3;
    private static final int ID_SOON = 1;
    private static final int ID_CHANGE = 2;

    private Notifications() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_changes_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(context.getString(R.string.channel_changes_desc));
            manager.createNotificationChannel(channel);
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo crear el canal de notificaciones", error);
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean canPost(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            if (Build.VERSION.SDK_INT >= 24) {
                NotificationManager manager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                return manager != null && manager.areNotificationsEnabled();
            }
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    public static void showChange(Context context, int daysBefore) {
        ensureChannel(context);
        if (!canPost(context)) {
            return;
        }
        try {
            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, openIntent, flags);

            String body;
            int notificationId;
            if (daysBefore >= 7) {
                body = context.getString(R.string.notif_body_week);
                notificationId = ID_WEEK;
            } else if (daysBefore >= 1) {
                body = context.getString(R.string.notif_body_soon);
                notificationId = ID_SOON;
            } else {
                body = context.getString(R.string.notif_body_change);
                notificationId = ID_CHANGE;
            }

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= 26) {
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
            }
            builder.setSmallIcon(R.drawable.ic_stat_time)
                    .setContentTitle(context.getString(R.string.notif_title))
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true);

            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(notificationId, builder.build());
            }
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo mostrar la notificación", error);
        }
    }
}
