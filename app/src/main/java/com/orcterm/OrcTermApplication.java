package com.orcterm;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 应用程序入口
 */
public class OrcTermApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化全局配置
        initTheme();
    }

    private void initTheme() {
        SharedPreferences prefs = getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
        int mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (mode == AppCompatDelegate.MODE_NIGHT_YES || mode == AppCompatDelegate.MODE_NIGHT_NO) {
            AppCompatDelegate.setDefaultNightMode(mode);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
