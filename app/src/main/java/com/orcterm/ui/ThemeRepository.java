package com.orcterm.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.UUID;

/**
 * 终端主题配置仓库
 */
public class ThemeRepository {

    public static class ThemeConfig {
        public int[] ansiColors;
        public int backgroundColor;
        public int foregroundColor;
        public int selectionColor;
        public int searchHighlightColor;
        public int cursorColor;
        public int cursorStyle;
        public boolean cursorBlink;
        public float fontSizeSp;
        public float lineHeight;
        public float letterSpacing;
        public int fontFamily;
        public int fontWeight;
        public String themeId;
        public String themePreset;
        public int backgroundAlpha;
    }

    private static final String PREFS_NAME = "orcterm_prefs";
    private static final String PREF_THEME_JSON = "terminal_theme_json";
    private static final String PREF_THEME_ID = "terminal_theme_id";
    private static final String PREF_THEME_PRESET = "terminal_theme_preset";
    private static final String PREF_CUSTOM_THEME_VERSION = "custom_theme_version";
    private static final String PREF_CUSTOM_COLOR_PREFIX = "custom_color_";
    private static final String PREF_CUSTOM_BG = "terminal_bg_color";
    private static final String PREF_CUSTOM_FG = "terminal_fg_color";
    private static final String PREF_FONT_SIZE_PX = "terminal_font_size_px";
    private static final String PREF_LINE_HEIGHT = "terminal_line_height";
    private static final String PREF_LETTER_SPACING = "terminal_letter_spacing";
    private static final String PREF_FONT_FAMILY = "terminal_font_family";
    private static final String PREF_FONT_WEIGHT = "terminal_font_weight";
    private static final String PREF_CURSOR_STYLE = "terminal_cursor_style";
    private static final String PREF_CURSOR_BLINK = "terminal_cursor_blink";
    private static final String PREF_CURSOR_COLOR = "terminal_cursor_color";
    private static final String PREF_SELECTION_COLOR = "terminal_selection_color";
    private static final String PREF_SEARCH_HIGHLIGHT_COLOR = "terminal_search_highlight_color";
    private static final String PREF_THEME_INDEX = "terminal_theme_index";
    private static final String PREF_BG_ALPHA = "terminal_bg_alpha";

    private static final int[] THEME_LIGHT = {
        0xFFFFFFFF, 0xFFD32F2F, 0xFF388E3C, 0xFFF57C00,
        0xFF1976D2, 0xFF7B1FA2, 0xFF0097A7, 0xFF202124,
        0xFF5F6368, 0xFFD93025, 0xFF1E8E3E, 0xFFF9AB00,
        0xFF1A73E8, 0xFF9334E6, 0xFF00B8D4, 0xFF000000
    };

    private static final int[] THEME_SOLARIZED_LIGHT = {
        0xFFFDF6E3, 0xFFDC322F, 0xFF859900, 0xFFB58900,
        0xFF268BD2, 0xFFD33682, 0xFF2AA198, 0xFF073642,
        0xFFEEE8D5, 0xFFCB4B16, 0xFF586E75, 0xFF657B83,
        0xFF839496, 0xFF6C71C4, 0xFF93A1A1, 0xFF002B36
    };

    private static final int[] THEME_HIGH_CONTRAST = {
        0xFF000000, 0xFFFF3B30, 0xFF34C759, 0xFFFFCC00,
        0xFF0A84FF, 0xFFFF2D55, 0xFF64D2FF, 0xFFFFFFFF,
        0xFF3A3A3C, 0xFFFF453A, 0xFF30D158, 0xFFFFD60A,
        0xFF5E5CE6, 0xFFFF375F, 0xFF70D7FF, 0xFFFFFFFF
    };

    private final SharedPreferences prefs;
    private final float scaledDensity;
    private final MutableLiveData<ThemeConfig> themeLive = new MutableLiveData<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;

    public ThemeRepository(Application application) {
        prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        scaledDensity = application.getResources().getDisplayMetrics().scaledDensity;
        listener = (p, key) -> {
            if (isThemeKey(key)) {
                themeLive.postValue(readConfig());
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
        themeLive.setValue(readConfig());
    }

    public LiveData<ThemeConfig> getTheme() {
        return themeLive;
    }

    public void refresh() {
        themeLive.setValue(readConfig());
    }

    public void close() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public void setFontSizeSp(float sizeSp) {
        int px = Math.max(8, Math.round(sizeSp * scaledDensity));
        prefs.edit().putInt(PREF_FONT_SIZE_PX, px).apply();
    }

    public void setFontFamily(int family) {
        prefs.edit().putInt(PREF_FONT_FAMILY, family).apply();
    }

    public void setFontWeight(int weight) {
        prefs.edit().putInt(PREF_FONT_WEIGHT, weight).apply();
    }

    public void setLineHeight(float value) {
        prefs.edit().putFloat(PREF_LINE_HEIGHT, value).apply();
    }

    public void setLetterSpacing(float value) {
        prefs.edit().putFloat(PREF_LETTER_SPACING, value).apply();
    }

    public void setCursorStyle(int style) {
        prefs.edit().putInt(PREF_CURSOR_STYLE, style).apply();
    }

    public void setCursorBlink(boolean blink) {
        prefs.edit().putBoolean(PREF_CURSOR_BLINK, blink).apply();
    }

    public void setCursorColor(int color) {
        prefs.edit().putInt(PREF_CURSOR_COLOR, color).apply();
    }

    public void setBackgroundColor(int color) {
        prefs.edit().putInt(PREF_CUSTOM_BG, color).apply();
    }

    public void setForegroundColor(int color) {
        prefs.edit().putInt(PREF_CUSTOM_FG, color).apply();
    }

    public void setSelectionColor(int color) {
        prefs.edit().putInt(PREF_SELECTION_COLOR, color).apply();
    }

    public void setSearchHighlightColor(int color) {
        prefs.edit().putInt(PREF_SEARCH_HIGHLIGHT_COLOR, color).apply();
    }

    public void setBackgroundAlpha(int alpha) {
        prefs.edit().putInt(PREF_BG_ALPHA, alpha).apply();
    }

    public void applyPreset(String presetId) {
        int[] palette = getPresetPalette(presetId);
        if (palette == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < 16; i++) {
            editor.putInt(PREF_CUSTOM_COLOR_PREFIX + i, palette[i]);
        }
        editor.putInt(PREF_CUSTOM_THEME_VERSION, 1);
        editor.putString(PREF_THEME_PRESET, presetId);
        JSONObject json = buildThemeJsonFromPalette(palette, presetId);
        editor.putString(PREF_THEME_JSON, json.toString());
        editor.apply();
    }

    public void setThemePreset(String presetId) {
        prefs.edit().putString(PREF_THEME_PRESET, presetId).apply();
    }

    private boolean isThemeKey(String key) {
        if (key == null) return false;
        if (key.startsWith(PREF_CUSTOM_COLOR_PREFIX)) return true;
        return PREF_THEME_JSON.equals(key)
            || PREF_THEME_ID.equals(key)
            || PREF_THEME_PRESET.equals(key)
            || PREF_CUSTOM_BG.equals(key)
            || PREF_CUSTOM_FG.equals(key)
            || PREF_FONT_SIZE_PX.equals(key)
            || PREF_LINE_HEIGHT.equals(key)
            || PREF_LETTER_SPACING.equals(key)
            || PREF_FONT_FAMILY.equals(key)
            || PREF_FONT_WEIGHT.equals(key)
            || PREF_CURSOR_STYLE.equals(key)
            || PREF_CURSOR_BLINK.equals(key)
            || PREF_CURSOR_COLOR.equals(key)
            || PREF_SELECTION_COLOR.equals(key)
            || PREF_SEARCH_HIGHLIGHT_COLOR.equals(key)
            || PREF_THEME_INDEX.equals(key)
            || PREF_BG_ALPHA.equals(key);
    }

    private ThemeConfig readConfig() {
        ThemeConfig config = new ThemeConfig();
        int[] palette = resolvePalette();
        config.ansiColors = palette;
        Integer customBg = prefs.contains(PREF_CUSTOM_BG) ? prefs.getInt(PREF_CUSTOM_BG, palette[0]) : null;
        Integer customFg = prefs.contains(PREF_CUSTOM_FG) ? prefs.getInt(PREF_CUSTOM_FG, palette[7]) : null;
        config.backgroundColor = customBg != null ? customBg : palette[0];
        config.foregroundColor = customFg != null ? customFg : palette[7];
        config.selectionColor = prefs.getInt(PREF_SELECTION_COLOR, 0x5533B5E5);
        config.searchHighlightColor = prefs.getInt(PREF_SEARCH_HIGHLIGHT_COLOR, 0x66FFD54F);
        config.cursorColor = prefs.getInt(PREF_CURSOR_COLOR, 0xFFFFFFFF);
        config.cursorStyle = prefs.getInt(PREF_CURSOR_STYLE, 0);
        config.cursorBlink = prefs.getBoolean(PREF_CURSOR_BLINK, true);
        int px = prefs.getInt(PREF_FONT_SIZE_PX, Math.round(14 * scaledDensity));
        config.fontSizeSp = scaledDensity == 0 ? 14f : px / scaledDensity;
        config.lineHeight = prefs.getFloat(PREF_LINE_HEIGHT, 1.0f);
        config.letterSpacing = prefs.getFloat(PREF_LETTER_SPACING, 0.0f);
        config.fontFamily = prefs.getInt(PREF_FONT_FAMILY, 0);
        config.fontWeight = prefs.getInt(PREF_FONT_WEIGHT, 400);
        config.themeId = prefs.getString(PREF_THEME_ID, "");
        config.themePreset = prefs.getString(PREF_THEME_PRESET, "");
        config.backgroundAlpha = prefs.getInt(PREF_BG_ALPHA, 255);
        return config;
    }

    private int[] resolvePalette() {
        int[] fromJson = readPaletteFromThemeJson();
        if (fromJson != null) return fromJson;
        int[] fromPrefs = readPaletteFromPrefs();
        if (fromPrefs != null) return fromPrefs;
        int index = prefs.getInt(PREF_THEME_INDEX, 0);
        if (index == 2) return THEME_SOLARIZED_LIGHT;
        return THEME_LIGHT;
    }

    private int[] readPaletteFromPrefs() {
        if (prefs.getInt(PREF_CUSTOM_THEME_VERSION, 0) <= 0) return null;
        int[] palette = new int[16];
        boolean ok = false;
        for (int i = 0; i < 16; i++) {
            String key = PREF_CUSTOM_COLOR_PREFIX + i;
            if (prefs.contains(key)) {
                palette[i] = prefs.getInt(key, 0);
                ok = true;
            } else {
                return null;
            }
        }
        return ok ? palette : null;
    }

    private int[] readPaletteFromThemeJson() {
        String json = prefs.getString(PREF_THEME_JSON, null);
        if (json == null || json.isEmpty()) return null;
        try {
            Object parsed = new JSONTokener(json).nextValue();
            if (!(parsed instanceof JSONObject)) return null;
            JSONObject obj = (JSONObject) parsed;
            JSONObject ansi = obj.optJSONObject("ansi");
            if (ansi == null) return null;
            JSONObject sets = ansi.optJSONObject("sets");
            String active = ansi.optString("active", "");
            JSONObject setObj = null;
            if (sets != null && !active.isEmpty()) {
                setObj = sets.optJSONObject(active);
            }
            if (setObj == null && sets != null && sets.length() > 0) {
                java.util.Iterator<String> it = sets.keys();
                if (it.hasNext()) {
                    setObj = sets.optJSONObject(it.next());
                }
            }
            if (setObj == null) {
                setObj = ansi;
            }
            JSONArray palette = setObj.optJSONArray("palette");
            if (palette != null && palette.length() >= 16) {
                int[] scheme = new int[16];
                for (int i = 0; i < 16; i++) {
                    scheme[i] = parseColorValue(palette.opt(i), THEME_LIGHT[i]);
                }
                return scheme;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int[] getPresetPalette(String presetId) {
        if ("light".equals(presetId)) return THEME_SOLARIZED_LIGHT;
        if ("high_contrast".equals(presetId)) return THEME_HIGH_CONTRAST;
        return THEME_LIGHT;
    }

    private JSONObject buildThemeJsonFromPalette(int[] colors, String presetId) {
        JSONObject obj = new JSONObject();
        JSONArray palette = new JSONArray();
        for (int i = 0; i < 16; i++) {
            palette.put(String.format("#%06X", colors[i] & 0x00FFFFFF));
        }
        JSONObject standard = new JSONObject();
        JSONObject bright = new JSONObject();
        try {
            standard.put("black", palette.get(0));
            standard.put("red", palette.get(1));
            standard.put("green", palette.get(2));
            standard.put("yellow", palette.get(3));
            standard.put("blue", palette.get(4));
            standard.put("magenta", palette.get(5));
            standard.put("cyan", palette.get(6));
            standard.put("white", palette.get(7));
            bright.put("brightBlack", palette.get(8));
            bright.put("brightRed", palette.get(9));
            bright.put("brightGreen", palette.get(10));
            bright.put("brightYellow", palette.get(11));
            bright.put("brightBlue", palette.get(12));
            bright.put("brightMagenta", palette.get(13));
            bright.put("brightCyan", palette.get(14));
            bright.put("brightWhite", palette.get(15));
            JSONObject ansiSet = new JSONObject();
            ansiSet.put("palette", palette);
            ansiSet.put("standard", standard);
            ansiSet.put("bright", bright);
            JSONObject sets = new JSONObject();
            sets.put("default", ansiSet);
            JSONObject ansi = new JSONObject();
            ansi.put("active", "default");
            ansi.put("sets", sets);
            JSONObject base = new JSONObject();
            base.put("background", palette.get(0));
            base.put("foreground", palette.get(7));
            base.put("selectionBackground", "#5533B5E5");
            base.put("selectionForeground", palette.get(7));
            base.put("currentLine", palette.get(8));
            base.put("lineNumber", palette.get(8));
            base.put("matchBracket", palette.get(3));
            base.put("searchHighlight", "#66FFD54F");
            obj.put("version", 1);
            obj.put("id", getOrCreateThemeId());
            obj.put("name", presetId == null ? "Custom" : presetId);
            obj.put("type", "dark");
            obj.put("tags", new JSONArray());
            long now = System.currentTimeMillis();
            obj.put("createdAt", now);
            obj.put("updatedAt", now);
            obj.put("colors", base);
            obj.put("ansi", ansi);
        } catch (Exception ignored) {
        }
        return obj;
    }

    private String getOrCreateThemeId() {
        String id = prefs.getString(PREF_THEME_ID, null);
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(PREF_THEME_ID, id).apply();
        }
        return id;
    }

    private int parseColorValue(Object value, int fallback) {
        Integer parsed = parseColorValue(value);
        return parsed != null ? parsed : fallback;
    }

    private Integer parseColorValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) return null;
            try {
                return Color.parseColor(str);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

}
