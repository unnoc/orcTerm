package com.orcterm;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.annotation.NonNull;

/**
 * 应用程序入口
 */
public class OrcTermApplication extends Application {
    private static OrcTermApplication instance;

    public static Context getAppContext() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // 初始化全局配置
        initTheme();
        initLanguage();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                com.orcterm.util.AppBackgroundHelper.applyFromPrefs(activity, activity.findViewById(android.R.id.content));
            }

            @Override public void onActivityCreated(@NonNull Activity activity, android.os.Bundle savedInstanceState) {}
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull android.os.Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
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

    private void initLanguage() {
        SharedPreferences prefs = getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
        String lang = prefs.getString("app_language", "system");
        if ("system".equals(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else if ("zh".equals(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"));
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
        }
    }
}
