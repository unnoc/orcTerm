package com.orcterm.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 会话保持策略管理器
 * 管理 SSH 会话在后台保持连接的策略设置
 */
public class SessionPersistenceManager {
    
    private static final String PREFS_NAME = "session_persistence_prefs";
    private static final String KEY_POLICY = "session_policy";
    private static final String KEY_TIMEOUT_MINUTES = "session_timeout_minutes";
    private static final String KEY_RECONNECT_ON_RESUME = "reconnect_on_resume";
    private static final String KEY_AUTO_DISCONNECT_ON_BG = "auto_disconnect_on_background";
    private static final String KEY_BG_DISCONNECT_DELAY = "bg_disconnect_delay_seconds";
    private static final String KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled";
    private static final String KEY_KEEP_ALIVE_INTERVAL = "keep_alive_interval_seconds";
    
    /**
     * 会话保持策略枚举
     */
    public enum SessionPolicy {
        ALWAYS_KEEP(0),           // 始终保持连接
        SMART_MANAGE(1),          // 智能管理（根据网络情况）
        TIMEOUT_DISCONNECT(2),    // 超时后断开
        IMMEDIATE_DISCONNECT(3);  // 立即断开
        
        private final int value;
        
        SessionPolicy(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static SessionPolicy fromValue(int value) {
            for (SessionPolicy policy : values()) {
                if (policy.value == value) {
                    return policy;
                }
            }
            return SMART_MANAGE;
        }
    }
    
    private final SharedPreferences prefs;
    private static SessionPersistenceManager instance;
    
    private SessionPersistenceManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized SessionPersistenceManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionPersistenceManager(context);
        }
        return instance;
    }
    
    /**
     * 获取当前会话保持策略
     */
    public SessionPolicy getSessionPolicy() {
        int policyValue = prefs.getInt(KEY_POLICY, SessionPolicy.SMART_MANAGE.getValue());
        return SessionPolicy.fromValue(policyValue);
    }
    
    /**
     * 设置会话保持策略
     */
    public void setSessionPolicy(SessionPolicy policy) {
        prefs.edit().putInt(KEY_POLICY, policy.getValue()).apply();
    }
    
    /**
     * 获取超时时间（分钟）- 仅对 TIMEOUT_DISCONNECT 策略有效
     */
    public int getTimeoutMinutes() {
        return prefs.getInt(KEY_TIMEOUT_MINUTES, 30);
    }
    
    /**
     * 设置超时时间（分钟）
     */
    public void setTimeoutMinutes(int minutes) {
        prefs.edit().putInt(KEY_TIMEOUT_MINUTES, Math.max(1, Math.min(1440, minutes))).apply();
    }
    
    /**
     * 是否在应用恢复时自动重连
     */
    public boolean isReconnectOnResume() {
        return prefs.getBoolean(KEY_RECONNECT_ON_RESUME, true);
    }
    
    /**
     * 设置是否在应用恢复时自动重连
     */
    public void setReconnectOnResume(boolean enabled) {
        prefs.edit().putBoolean(KEY_RECONNECT_ON_RESUME, enabled).apply();
    }
    
    /**
     * 是否在切换到后台时自动断开
     */
    public boolean isAutoDisconnectOnBackground() {
        return prefs.getBoolean(KEY_AUTO_DISCONNECT_ON_BG, false);
    }
    
    /**
     * 设置是否在切换到后台时自动断开
     */
    public void setAutoDisconnectOnBackground(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_DISCONNECT_ON_BG, enabled).apply();
    }
    
    /**
     * 获取后台断开延迟时间（秒）
     */
    public int getBackgroundDisconnectDelay() {
        return prefs.getInt(KEY_BG_DISCONNECT_DELAY, 60);
    }
    
    /**
     * 设置后台断开延迟时间（秒）
     */
    public void setBackgroundDisconnectDelay(int seconds) {
        prefs.edit().putInt(KEY_BG_DISCONNECT_DELAY, Math.max(0, Math.min(3600, seconds))).apply();
    }
    
    /**
     * 是否启用心跳保活
     */
    public boolean isKeepAliveEnabled() {
        return prefs.getBoolean(KEY_KEEP_ALIVE_ENABLED, true);
    }
    
    /**
     * 设置是否启用心跳保活
     */
    public void setKeepAliveEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled).apply();
    }
    
    /**
     * 获取心跳间隔（秒）
     */
    public int getKeepAliveInterval() {
        return prefs.getInt(KEY_KEEP_ALIVE_INTERVAL, 30);
    }
    
    /**
     * 设置心跳间隔（秒）
     */
    public void setKeepAliveInterval(int seconds) {
        prefs.edit().putInt(KEY_KEEP_ALIVE_INTERVAL, Math.max(5, Math.min(300, seconds))).apply();
    }
    
    /**
     * 获取策略描述文本
     */
    public String getPolicyDescription(SessionPolicy policy) {
        switch (policy) {
            case ALWAYS_KEEP:
                return "始终保持连接，即使应用在后台";
            case SMART_MANAGE:
                return "智能管理：WiFi下保持，移动网络下超时断开";
            case TIMEOUT_DISCONNECT:
                return "空闲" + getTimeoutMinutes() + "分钟后自动断开";
            case IMMEDIATE_DISCONNECT:
                return "切换到后台时立即断开连接";
            default:
                return "智能管理";
        }
    }
    
    /**
     * 检查当前策略是否应该在后台保持连接
     */
    public boolean shouldKeepConnectionInBackground() {
        SessionPolicy policy = getSessionPolicy();
        return policy == SessionPolicy.ALWAYS_KEEP || policy == SessionPolicy.SMART_MANAGE;
    }
    
    /**
     * 重置为默认设置
     */
    public void resetToDefaults() {
        prefs.edit()
            .putInt(KEY_POLICY, SessionPolicy.SMART_MANAGE.getValue())
            .putInt(KEY_TIMEOUT_MINUTES, 30)
            .putBoolean(KEY_RECONNECT_ON_RESUME, true)
            .putBoolean(KEY_AUTO_DISCONNECT_ON_BG, false)
            .putInt(KEY_BG_DISCONNECT_DELAY, 60)
            .putBoolean(KEY_KEEP_ALIVE_ENABLED, true)
            .putInt(KEY_KEEP_ALIVE_INTERVAL, 30)
            .apply();
    }
}
