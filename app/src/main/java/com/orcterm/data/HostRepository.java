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

    public HostRepository(Application application) {
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
        });
    }

    public void delete(HostEntity host) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            mHostDao.delete(host);
        });
    }

    public void update(HostEntity host) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            mHostDao.update(host);
        });
    }
}
