package com.orcterm.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

/**
 * 终端主题配置 ViewModel
 */
public class ThemeViewModel extends AndroidViewModel {

    private final ThemeRepository repository;
    private final LiveData<ThemeRepository.ThemeConfig> theme;

    public ThemeViewModel(@NonNull Application application) {
        super(application);
        repository = new ThemeRepository(application);
        theme = repository.getTheme();
    }

    public LiveData<ThemeRepository.ThemeConfig> getTheme() {
        return theme;
    }

    public void refresh() {
        repository.refresh();
    }

    public void setFontSizeSp(float sizeSp) {
        repository.setFontSizeSp(sizeSp);
    }

    public void setFontFamily(int family) {
        repository.setFontFamily(family);
    }

    public void setFontWeight(int weight) {
        repository.setFontWeight(weight);
    }

    public void setLineHeight(float value) {
        repository.setLineHeight(value);
    }

    public void setLetterSpacing(float value) {
        repository.setLetterSpacing(value);
    }

    public void setCursorStyle(int style) {
        repository.setCursorStyle(style);
    }

    public void setCursorBlink(boolean blink) {
        repository.setCursorBlink(blink);
    }

    public void setCursorColor(int color) {
        repository.setCursorColor(color);
    }

    public void setBackgroundColor(int color) {
        repository.setBackgroundColor(color);
    }

    public void setForegroundColor(int color) {
        repository.setForegroundColor(color);
    }

    public void setSelectionColor(int color) {
        repository.setSelectionColor(color);
    }

    public void setSearchHighlightColor(int color) {
        repository.setSearchHighlightColor(color);
    }

    public void setBackgroundAlpha(int alpha) {
        repository.setBackgroundAlpha(alpha);
    }

    public void applyPreset(String presetId) {
        repository.applyPreset(presetId);
    }

    public void setThemePreset(String presetId) {
        repository.setThemePreset(presetId);
    }

    @Override
    protected void onCleared() {
        repository.close();
    }
}
