package com.orcterm.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import com.orcterm.R;
import com.orcterm.data.AppDatabase;
import com.orcterm.data.HostEntity;
import com.orcterm.ui.HostDetailActivity;

import java.util.Random;
import java.util.concurrent.Executors;

public class ServerWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        long hostId = prefs.getLong("widget_host_" + appWidgetId, -1);

        if (hostId == -1) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            HostEntity host = AppDatabase.getDatabase(context).hostDao().findById(hostId);
            
            // Fallback: iterate all hosts
            if (host == null) {
                for (HostEntity h : AppDatabase.getDatabase(context).hostDao().getAllHostsNow()) {
                    if (h.id == hostId) {
                        host = h;
                        break;
                    }
                }
            }

            if (host != null) {
                HostEntity finalHost = host;
                // Generate Mock Data for now as per MonitorActivity logic
                Random random = new Random();
                int cpu = random.nextInt(100);
                int mem = 20 + random.nextInt(60);
                boolean online = random.nextBoolean(); // Mock status

                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_server_status);
                
                String name = (finalHost.alias != null && !finalHost.alias.isEmpty()) ? finalHost.alias : finalHost.hostname;
                views.setTextViewText(R.id.widget_server_name, name);
                views.setTextViewText(R.id.widget_server_detail, finalHost.username + "@" + finalHost.hostname);
                
                views.setImageViewResource(R.id.widget_status_indicator, R.drawable.circle_bg);
                if (online) {
                    views.setInt(R.id.widget_status_indicator, "setColorFilter", 0xFF4CAF50); // Green
                } else {
                    views.setInt(R.id.widget_status_indicator, "setColorFilter", 0xFFF44336); // Red
                }

                views.setProgressBar(R.id.widget_progress_cpu, 100, cpu, false);
                views.setTextViewText(R.id.widget_text_cpu, cpu + "%");
                
                views.setProgressBar(R.id.widget_progress_mem, 100, mem, false);
                views.setTextViewText(R.id.widget_text_mem, mem + "%");
                
                String time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
                views.setTextViewText(R.id.widget_update_time, "Updated: " + time);

                // Click Intent
                Intent clickIntent = new Intent(context, HostDetailActivity.class);
                clickIntent.putExtra("host_id", finalHost.id);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

                appWidgetManager.updateAppWidget(appWidgetId, views);
            }
        });
    }
}
