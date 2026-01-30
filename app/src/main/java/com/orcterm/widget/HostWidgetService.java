package com.orcterm.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class HostWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new HostWidgetFactory(this.getApplicationContext(), intent);
    }
}