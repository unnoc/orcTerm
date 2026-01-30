package com.orcterm.ui.nav;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class NavViewModel extends ViewModel {

    private final MutableLiveData<Integer> selectedIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Long> currentHostId = new MutableLiveData<>(null);

    public LiveData<Integer> getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        selectedIndex.setValue(index);
    }

    public LiveData<Long> getCurrentHostId() {
        return currentHostId;
    }

    public void setCurrentHostId(Long hostId) {
        currentHostId.setValue(hostId);
    }
}
