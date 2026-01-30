package com.orcterm.widget;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.orcterm.R;
import com.orcterm.data.AppDatabase;
import com.orcterm.data.HostEntity;
import com.orcterm.ui.TerminalActivity;

import java.util.ArrayList;
import java.util.List;

public class HostWidgetFactory implements RemoteViewsService.RemoteViewsFactory {

    private final Context context;
    private List<HostEntity> hosts = new ArrayList<>();

    public HostWidgetFactory(Context context, Intent intent) {
        this.context = context;
    }

    @Override
    public void onCreate() {
        // Init data
    }

    @Override
    public void onDataSetChanged() {
        // Refresh data
        try {
            hosts = AppDatabase.getDatabase(context).hostDao().getAllHostsNow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        hosts.clear();
    }

    @Override
    public int getCount() {
        return hosts.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position >= hosts.size()) return null;

        HostEntity host = hosts.get(position);
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.item_widget_host);

        String title = (host.alias != null && !host.alias.isEmpty()) ? host.alias : host.hostname;
        String detail = host.username + "@" + host.hostname + ":" + host.port;

        rv.setTextViewText(R.id.widget_item_alias, title);
        rv.setTextViewText(R.id.widget_item_detail, detail);
        
        // Fill intent
        Intent fillInIntent = new Intent();
        fillInIntent.putExtra("host_id", host.id);
        fillInIntent.putExtra("host_alias", title);
        fillInIntent.putExtra("host_name", host.hostname);
        fillInIntent.putExtra("host_port", host.port);
        fillInIntent.putExtra("host_user", host.username);
        fillInIntent.putExtra("host_pass", host.password);
        fillInIntent.putExtra("host_key", host.keyPath);
        fillInIntent.putExtra("host_auth", host.authType);
        rv.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent);

        return rv;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        if (position >= hosts.size()) return position;
        return hosts.get(position).id;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}