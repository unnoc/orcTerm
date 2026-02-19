package com.orcterm.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.orcterm.R;
import com.orcterm.ui.SshTerminalActivity;

/**
 * 常驻通知管理
 */
public class PersistentNotificationHelper {

    private static final String CHANNEL_ID = "ssh_status";
    private static final int NOTIFICATION_ID = 10001;

    public static void refresh(Context ctx) {
        ensureChannel(ctx);
        android.content.SharedPreferences prefs = ctx.getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("persistent_notification_enabled", false);
        boolean active = prefs.getBoolean("persistent_notification_active", false);
        String info = prefs.getString("persistent_notification_info", null);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (enabled && active && info != null && !info.isEmpty()) {
            Intent intent = new Intent(ctx, SshTerminalActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 0, intent,
                    Build.VERSION.SDK_INT >= 31
                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_action_computer)
                    .setContentTitle(ctx.getString(R.string.notification_ssh_active_title))
                    .setContentText(ctx.getString(R.string.notification_ssh_active_content, info))
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            nm.notify(NOTIFICATION_ID, builder.build());
        } else {
            nm.cancel(NOTIFICATION_ID);
        }
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(
                        CHANNEL_ID,
                        ctx.getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(ctx.getString(R.string.notification_channel_description));
                nm.createNotificationChannel(channel);
            }
        }
    }
}
