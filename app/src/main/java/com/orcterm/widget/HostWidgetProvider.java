package com.orcterm.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import com.orcterm.R;
import com.orcterm.ui.AddHostActivity;
import com.orcterm.ui.SshTerminalActivity;

/**
 * 主机桌面小部件 Provider
 */
public class HostWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.orcterm.widget.ACTION_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_hosts);

        // Set up the collection
        Intent intent = new Intent(context, HostWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_list, intent);
        views.setEmptyView(R.id.widget_list, R.id.widget_empty_view);

        // Set up Refresh action
        Intent refreshIntent = new Intent(context, HostWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent);

        // Set up Add Host action
        Intent addIntent = new Intent(context, AddHostActivity.class);
        PendingIntent addPendingIntent = PendingIntent.getActivity(context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_add, addPendingIntent);

        // Set up item click template
        Intent clickIntent = new Intent(context, SshTerminalActivity.class);
        PendingIntent clickPendingIntent = PendingIntent.getActivity(context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisAppWidget = new ComponentName(context.getPackageName(), HostWidgetProvider.class.getName());
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);
        }
    }
}
