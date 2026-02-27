package com.orcterm.data;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * 主机数据仓库，负责管理数据源
 */
public class HostRepository {

    private HostDao mHostDao;
    private LiveData<List<HostEntity>> mAllHosts;
    private Application mApplication;

    public HostRepository(Application application) {
        mApplication = application;
        AppDatabase db = AppDatabase.getDatabase(application);
        mHostDao = db.hostDao();
        mAllHosts = mHostDao.getAllHosts();
    }

    public LiveData<List<HostEntity>> getAllHosts() {
        return mAllHosts;
    }

    public void insert(HostEntity host) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            mHostDao.insert(host);
            sendWidgetUpdateBroadcast();
        });
    }

    public void delete(HostEntity host) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            mHostDao.delete(host);
            sendWidgetUpdateBroadcast();
        });
    }

    public void update(HostEntity host) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            mHostDao.update(host);
            sendWidgetUpdateBroadcast();
        });
    }

    private void sendWidgetUpdateBroadcast() {
        android.content.Intent intent = new android.content.Intent("com.orcterm.widget.ACTION_REFRESH");
        intent.setPackage(mApplication.getPackageName());
        mApplication.sendBroadcast(intent);
    }
}
