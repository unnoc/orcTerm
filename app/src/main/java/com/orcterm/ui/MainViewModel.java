package com.orcterm.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.orcterm.data.HostEntity;
import com.orcterm.data.HostRepository;

import java.util.List;

/**
 * 主界面 ViewModel，管理主机数据
 */
public class MainViewModel extends AndroidViewModel {

    private HostRepository mRepository;
    private LiveData<List<HostEntity>> mAllHosts;

    public MainViewModel(@NonNull Application application) {
        super(application);
        mRepository = new HostRepository(application);
        mAllHosts = mRepository.getAllHosts();
    }

    public LiveData<List<HostEntity>> getAllHosts() {
        return mAllHosts;
    }

    public void insert(HostEntity host) {
        mRepository.insert(host);
    }

    public void delete(HostEntity host) {
        mRepository.delete(host);
    }

    public void update(HostEntity host) {
        mRepository.update(host);
    }
}
