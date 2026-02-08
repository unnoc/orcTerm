package com.orcterm.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ListView;

import androidx.annotation.Nullable;

import com.orcterm.R;
import com.orcterm.data.AppDatabase;
import com.orcterm.data.HostEntity;
import com.orcterm.ui.HostAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 主机小部件配置界面
 */
public class ServerWidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ListView listView;
    private HostAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_host); // Reusing layout or creating simple list layout? 
        // Using a simple list layout programmatically or reusing existing simple layout would be better.
        // Let's create a simple layout content view programmatically to avoid creating new file if possible,
        // or just use a simple list view.
        
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        listView = new ListView(this);
        setContentView(listView);

        loadHosts();
    }

    private void loadHosts() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<HostEntity> hosts = AppDatabase.getDatabase(this).hostDao().getAllHostsNow();
            runOnUiThread(() -> {
                if (hosts == null || hosts.isEmpty()) {
                    // Show empty message
                    return;
                }
                
                // Simple adapter
                android.widget.ArrayAdapter<String> arrayAdapter = new android.widget.ArrayAdapter<>(
                        this, android.R.layout.simple_list_item_1, new ArrayList<>());
                
                for (HostEntity host : hosts) {
                    String name = (host.alias != null && !host.alias.isEmpty()) ? host.alias : host.hostname;
                    arrayAdapter.add(name + " (" + host.username + "@" + host.hostname + ")");
                }
                
                listView.setAdapter(arrayAdapter);
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    saveSelection(hosts.get(position));
                });
            });
        });
    }

    private void saveSelection(HostEntity host) {
        SharedPreferences prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE);
        prefs.edit().putLong("widget_host_" + appWidgetId, host.id).apply();

        // Trigger update
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, ServerWidgetProvider.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        sendBroadcast(intent);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
