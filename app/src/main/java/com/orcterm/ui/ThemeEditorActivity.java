package com.orcterm.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.slider.Slider;
import com.orcterm.R;
import com.orcterm.ui.widget.TerminalView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ThemeEditorActivity extends AppCompatActivity {

    private static final String PREF_APP = "orcterm_prefs";
    private static final String PREF_THEME_JSON = "terminal_theme_json";
    private static final String PREF_THEME_ID = "terminal_theme_id";
    private static final String PREF_THEME_PRESET = "terminal_theme_preset";
    private static final String PREF_THEME_CREATED_AT = "terminal_theme_created_at";
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
    private static final String PREF_BG_ALPHA = "terminal_bg_alpha";

    private SharedPreferences terminalPrefs;
    private ThemeRepository.ThemeConfig latestConfig;
    private int[] currentScheme = new int[16];
    private Integer customBackgroundColor;
    private Integer customForegroundColor;
    private int currentSelectionColor = 0x5533B5E5;
    private int currentSearchHighlightColor = 0x66FFD54F;
    private int currentFontWeight = 400;
    private int currentFontFamily = 0;
    private float currentLineHeight = 1.0f;
    private float currentLetterSpacing = 0.0f;
    private int terminalBackgroundAlpha = 255;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_theme_config);
        terminalPrefs = getSharedPreferences(PREF_APP, MODE_PRIVATE);
        setTitle(getString(R.string.settings_terminal_theme_editor_title));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TerminalView preview = findViewById(R.id.theme_preview);
        android.widget.RadioGroup presetGroup = findViewById(R.id.theme_preset_group);
        android.widget.RadioButton presetDark = findViewById(R.id.theme_preset_dark);
        android.widget.RadioButton presetLight = findViewById(R.id.theme_preset_light);
        android.widget.RadioButton presetContrast = findViewById(R.id.theme_preset_contrast);
        Slider fontSize = findViewById(R.id.slider_font_size);
        android.widget.RadioGroup fontFamily = findViewById(R.id.font_family_group);
        android.widget.RadioButton fontMono = findViewById(R.id.font_family_mono);
        android.widget.RadioButton fontSystem = findViewById(R.id.font_family_system);
        Slider fontWeight = findViewById(R.id.slider_font_weight);
        Slider lineHeight = findViewById(R.id.slider_line_height);
        Slider letterSpacing = findViewById(R.id.slider_letter_spacing);
        android.widget.RadioGroup cursorStyle = findViewById(R.id.cursor_style_group);
        android.widget.RadioButton cursorBlock = findViewById(R.id.cursor_block);
        android.widget.RadioButton cursorUnderline = findViewById(R.id.cursor_underline);
        android.widget.RadioButton cursorBar = findViewById(R.id.cursor_bar);
        android.widget.Switch cursorBlink = findViewById(R.id.switch_cursor_blink);
        EditText bgR = findViewById(R.id.bg_r);
        EditText bgG = findViewById(R.id.bg_g);
        EditText bgB = findViewById(R.id.bg_b);
        EditText fgR = findViewById(R.id.fg_r);
        EditText fgG = findViewById(R.id.fg_g);
        EditText fgB = findViewById(R.id.fg_b);
        EditText hlR = findViewById(R.id.hl_r);
        EditText hlG = findViewById(R.id.hl_g);
        EditText hlB = findViewById(R.id.hl_b);
        android.widget.Button bgApply = findViewById(R.id.bg_apply);
        android.widget.Button fgApply = findViewById(R.id.fg_apply);
        android.widget.Button hlApply = findViewById(R.id.hl_apply);
        android.widget.Button importButton = findViewById(R.id.theme_import);
        android.widget.Button exportButton = findViewById(R.id.theme_export);

        ThemeViewModel viewModel = new ViewModelProvider(this).get(ThemeViewModel.class);
        final boolean[] binding = {true};
        Observer<ThemeRepository.ThemeConfig> observer = config -> {
            if (config == null) return;
            latestConfig = config;
            binding[0] = true;
            updateLocalConfig(config);
            updatePreview(preview, config);
            fontSize.setValue(Math.round(clampFloat(config.fontSizeSp, 10f, 20f)));
            fontWeight.setValue(Math.round(clampFloat(config.fontWeight, 300f, 700f) / 100f) * 100f);
            lineHeight.setValue(clampFloat(config.lineHeight, 1.0f, 2.0f));
            letterSpacing.setValue(clampFloat(config.letterSpacing, 0.0f, 2.0f));
            if (config.fontFamily == 0) {
                fontMono.setChecked(true);
            } else {
                fontSystem.setChecked(true);
            }
            if (config.cursorStyle == 1) {
                cursorUnderline.setChecked(true);
            } else if (config.cursorStyle == 2) {
                cursorBar.setChecked(true);
            } else {
                cursorBlock.setChecked(true);
            }
            cursorBlink.setChecked(config.cursorBlink);
            setRgbInputs(bgR, bgG, bgB, config.backgroundColor);
            setRgbInputs(fgR, fgG, fgB, config.foregroundColor);
            setRgbInputs(hlR, hlG, hlB, config.searchHighlightColor);
            if ("light".equals(config.themePreset)) {
                presetLight.setChecked(true);
            } else if ("high_contrast".equals(config.themePreset)) {
                presetContrast.setChecked(true);
            } else {
                presetDark.setChecked(true);
            }
            binding[0] = false;
        };
        viewModel.getTheme().observe(this, observer);

        presetGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding[0]) return;
            if (checkedId == presetLight.getId()) {
                viewModel.applyPreset("light");
                viewModel.setThemePreset("light");
            } else if (checkedId == presetContrast.getId()) {
                viewModel.applyPreset("high_contrast");
                viewModel.setThemePreset("high_contrast");
            } else {
                viewModel.applyPreset("dark");
                viewModel.setThemePreset("dark");
            }
        });
        fontSize.addOnChangeListener((s, value, fromUser) -> {
            if (binding[0]) return;
            viewModel.setFontSizeSp(value);
        });
        fontWeight.addOnChangeListener((s, value, fromUser) -> {
            if (binding[0]) return;
            viewModel.setFontWeight(Math.round(value));
        });
        lineHeight.addOnChangeListener((s, value, fromUser) -> {
            if (binding[0]) return;
            viewModel.setLineHeight(value);
        });
        letterSpacing.addOnChangeListener((s, value, fromUser) -> {
            if (binding[0]) return;
            viewModel.setLetterSpacing(value);
        });
        fontFamily.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding[0]) return;
            viewModel.setFontFamily(checkedId == fontSystem.getId() ? 1 : 0);
        });
        cursorStyle.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding[0]) return;
            if (checkedId == cursorUnderline.getId()) {
                viewModel.setCursorStyle(1);
            } else if (checkedId == cursorBar.getId()) {
                viewModel.setCursorStyle(2);
            } else {
                viewModel.setCursorStyle(0);
            }
        });
        cursorBlink.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (binding[0]) return;
            viewModel.setCursorBlink(isChecked);
        });
        bgApply.setOnClickListener(v -> {
            Integer color = parseRgbColor(bgR, bgG, bgB, customBackgroundColor);
            if (color != null) {
                viewModel.setBackgroundColor(color);
            }
        });
        fgApply.setOnClickListener(v -> {
            Integer color = parseRgbColor(fgR, fgG, fgB, customForegroundColor);
            if (color != null) {
                viewModel.setForegroundColor(color);
            }
        });
        hlApply.setOnClickListener(v -> {
            Integer color = parseRgbColor(hlR, hlG, hlB, currentSearchHighlightColor);
            if (color != null) {
                viewModel.setSearchHighlightColor(color);
            }
        });
        importButton.setOnClickListener(v -> showImportThemeDialog());
        exportButton.setOnClickListener(v -> showExportThemeDialog());

        handleInitialAction(getIntent());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleInitialAction(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("theme_action");
        if (TextUtils.isEmpty(action)) return;
        if ("import".equals(action)) {
            showImportThemeDialog();
        } else if ("export".equals(action)) {
            showExportThemeDialog();
        }
    }

    private void updateLocalConfig(ThemeRepository.ThemeConfig config) {
        if (config.ansiColors != null && config.ansiColors.length >= 16) {
            currentScheme = config.ansiColors.clone();
        }
        customBackgroundColor = config.backgroundColor;
        customForegroundColor = config.foregroundColor;
        currentSelectionColor = config.selectionColor;
        currentSearchHighlightColor = config.searchHighlightColor;
        currentLineHeight = config.lineHeight;
        currentLetterSpacing = config.letterSpacing;
        currentFontFamily = config.fontFamily;
        currentFontWeight = config.fontWeight;
        terminalBackgroundAlpha = config.backgroundAlpha;
    }

    private void updatePreview(TerminalView preview, ThemeRepository.ThemeConfig config) {
        if (preview == null || config == null) return;
        int[] scheme = buildEffectiveScheme(config);
        preview.setColorScheme(scheme);
        int sizePx = Math.max(8, Math.round(config.fontSizeSp * getResources().getDisplayMetrics().scaledDensity));
        preview.setFontSize(sizePx);
        preview.setLineHeightMultiplier(Math.max(1.2f, config.lineHeight));
        preview.setLetterSpacing(config.letterSpacing);
        Typeface base = config.fontFamily == 0 ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
        Typeface tf = Typeface.create(base, config.fontWeight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        preview.setTypeface(tf);
        preview.setSelectionColor(config.selectionColor);
        preview.setSearchHighlightColor(config.searchHighlightColor);
        TerminalView.CursorStyle[] styles = TerminalView.CursorStyle.values();
        if (config.cursorStyle >= 0 && config.cursorStyle < styles.length) {
            preview.setCursorStyle(styles[config.cursorStyle]);
        }
        preview.setCursorBlink(config.cursorBlink);
        preview.setCursorColor(config.cursorColor);
        int lineNumber = config.ansiColors != null && config.ansiColors.length > 8 ? config.ansiColors[8] : 0xFF888888;
        int bracketMatch = config.ansiColors != null && config.ansiColors.length > 3 ? config.ansiColors[3] : 0x66FFD54F;
        int highRisk = config.ansiColors != null && config.ansiColors.length > 1 ? config.ansiColors[1] : 0x55FF5252;
        // 在预览中暂时关闭行号显示，避免布局混乱
        preview.setShowLineNumbers(false);
        // preview.setLineNumberColor(lineNumber);
        preview.setShowBracketMatch(false);
        // preview.setBracketMatchColor(bracketMatch);
        preview.setShowHighRiskHighlight(false);
        // preview.setHighRiskHighlightColor(highRisk);
        // 根据屏幕大小调整预览区域高度
        float density = getResources().getDisplayMetrics().density;
        boolean isSmallScreen = density < 2.0f;
        int previewHeightDp = isSmallScreen ? 200 : 280;
        
        // 动态设置预览区域高度
        android.view.ViewGroup.LayoutParams params = preview.getLayoutParams();
        if (params != null) {
            params.height = (int) (previewHeightDp * getResources().getDisplayMetrics().density);
            preview.setLayoutParams(params);
        }
        
        preview.setPreviewContent(buildPreviewText(config));
    }

    private String buildPreviewText(ThemeRepository.ThemeConfig config) {
        String family = config.fontFamily == 0 ? "等宽" : "系统";
        int sizeSp = Math.round(config.fontSizeSp);
        String lineHeight = String.format(java.util.Locale.getDefault(), "%.1f", config.lineHeight);
        
        // 根据屏幕密度调整预览内容长度
        float density = getResources().getDisplayMetrics().density;
        boolean isSmallScreen = density < 2.0f; // 低密度屏幕认为是小屏
        
        if (isSmallScreen) {
            // 小屏幕简化版本
            return "orcTerm\n"
                + family + " " + sizeSp + "sp\n"
                + "$ ls\n"
                + "drwxr-xr-x .\n"
                + "drwxr-xr-x ..\n"
                + "-rw-r--r-- file.txt\n"
                + "$ _";
        } else {
            // 正常屏幕完整版本
            return "orcTerm 终端预览\n"
                + "字体:" + family + "  大小:" + sizeSp + "  行高:" + lineHeight + "\n"
                + "$ ls -l\n"
                + "total 24\n"
                + String.format("%s %2d %-8s %-8s %6d %s", "drwxr-xr-x", 2, "user", "grp", 4096, ".") + "\n"
                + String.format("%s %2d %-8s %-8s %6d %s", "drwxr-xr-x", 5, "user", "grp", 4096, "..") + "\n"
                + String.format("%s %2d %-8s %-8s %6d %s", "-rw-r--r--", 1, "user", "grp", 156, "file.txt") + "\n"
                + "$ _";
        }
    }

    private int[] buildEffectiveScheme(ThemeRepository.ThemeConfig config) {
        int[] scheme = new int[16];
        if (config.ansiColors != null && config.ansiColors.length >= 16) {
            System.arraycopy(config.ansiColors, 0, scheme, 0, 16);
        } else {
            System.arraycopy(currentScheme, 0, scheme, 0, Math.min(16, currentScheme.length));
        }
        scheme[0] = config.backgroundColor;
        scheme[7] = config.foregroundColor;
        scheme[15] = config.foregroundColor;
        return ensureReadableScheme(scheme);
    }

    private int[] ensureReadableScheme(int[] scheme) {
        int bg = scheme[0];
        int fg = scheme[7];
        if (!hasEnoughContrast(bg, fg)) {
            int fallback = isDark(bg) ? 0xFFFFFFFF : 0xFF000000;
            scheme[7] = fallback;
            scheme[15] = fallback;
        }
        return scheme;
    }

    private boolean hasEnoughContrast(int bg, int fg) {
        if (bg == fg) return false;
        double l1 = relativeLuminance(bg);
        double l2 = relativeLuminance(fg);
        double bright = Math.max(l1, l2);
        double dark = Math.min(l1, l2);
        double ratio = (bright + 0.05) / (dark + 0.05);
        return ratio >= 2.5;
    }

    private boolean isDark(int color) {
        return relativeLuminance(color) < 0.5;
    }

    private double relativeLuminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private void setRgbInputs(EditText r, EditText g, EditText b, int color) {
        r.setText(String.valueOf(Color.red(color)));
        g.setText(String.valueOf(Color.green(color)));
        b.setText(String.valueOf(Color.blue(color)));
    }

    private Integer parseRgbColor(EditText r, EditText g, EditText b, Integer fallback) {
        try {
            int rv = clampInt(Integer.parseInt(r.getText().toString().trim()), 0, 255);
            int gv = clampInt(Integer.parseInt(g.getText().toString().trim()), 0, 255);
            int bv = clampInt(Integer.parseInt(b.getText().toString().trim()), 0, 255);
            return Color.rgb(rv, gv, bv);
        } catch (Exception e) {
            if (fallback != null) return fallback;
            return null;
        }
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void showImportThemeDialog() {
        EditText edit = new EditText(this);
        edit.setHint("粘贴主题JSON结构/数组或以逗号分隔的16个颜色值");
        edit.setHeight(120);
        new AlertDialog.Builder(this)
            .setTitle("导入主题")
            .setView(edit)
            .setPositiveButton("导入", (d, w) -> {
                String input = edit.getText().toString().trim();
                if (input.startsWith("{") || input.startsWith("[")) {
                    importThemeFromJson(input);
                } else {
                    importThemeFromCsv(input);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void importThemeFromJson(String json) {
        try {
            Object parsed = new org.json.JSONTokener(json).nextValue();
            if (parsed instanceof JSONObject) {
                JSONObject obj = normalizeThemeJsonObject((JSONObject) parsed);
                applyThemeJsonObject(obj);
                terminalPrefs.edit().putString(PREF_THEME_JSON, obj.toString()).apply();
            } else if (parsed instanceof JSONArray) {
                JSONArray array = (JSONArray) parsed;
                if (array.length() >= 16) {
                    int[] colors = new int[16];
                    for (int i = 0; i < 16; i++) {
                        colors[i] = parseColorFromJsonValue(array.get(i));
                    }
                    JSONObject obj = buildThemeJsonFromPalette(colors, "Imported");
                    applyThemeJsonObject(obj);
                    terminalPrefs.edit().putString(PREF_THEME_JSON, obj.toString()).apply();
                } else {
                    Toast.makeText(this, "主题需要包含至少16个颜色", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "无效的JSON格式", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "无效的JSON格式", Toast.LENGTH_SHORT).show();
        }
    }

    private void importThemeFromCsv(String csv) {
        String[] parts = csv.split(",");
        if (parts.length >= 16) {
            int[] colors = new int[16];
            for (int i = 0; i < 16; i++) {
                colors[i] = parseColorFromValue(parts[i].trim());
            }
            JSONObject obj = buildThemeJsonFromPalette(colors, "Imported");
            applyThemeJsonObject(obj);
            terminalPrefs.edit().putString(PREF_THEME_JSON, obj.toString()).apply();
        } else {
            Toast.makeText(this, "需要至少16个颜色值", Toast.LENGTH_SHORT).show();
        }
    }

    private void showExportThemeDialog() {
        int[] palette = currentScheme;
        if (latestConfig != null && latestConfig.ansiColors != null && latestConfig.ansiColors.length >= 16) {
            palette = latestConfig.ansiColors;
        }
        JSONObject jsonObject = buildThemeJsonFromPalette(palette, "Custom");
        String jsonExport = jsonObject.toString();
        StringBuilder csvBuilder = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0) csvBuilder.append(", ");
            csvBuilder.append(String.format("#%06X", palette[i] & 0x00FFFFFF));
        }
        String csvExport = csvBuilder.toString();
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText("选择导出格式:");
        layout.addView(label);
        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(this);
        android.widget.RadioButton jsonRadio = new android.widget.RadioButton(this);
        jsonRadio.setText("JSON结构");
        jsonRadio.setId(1);
        android.widget.RadioButton csvRadio = new android.widget.RadioButton(this);
        csvRadio.setText("CSV格式");
        csvRadio.setId(2);
        radioGroup.addView(jsonRadio);
        radioGroup.addView(csvRadio);
        radioGroup.check(1);
        layout.addView(radioGroup);
        android.widget.TextView preview = new android.widget.TextView(this);
        preview.setText(jsonExport);
        preview.setPadding(0, 16, 0, 8);
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setTextSize(12);
        layout.addView(preview);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            preview.setText(checkedId == 1 ? jsonExport : csvExport);
        });
        new AlertDialog.Builder(this)
            .setTitle("导出主题")
            .setView(layout)
            .setPositiveButton("复制到剪贴板", (d, w) -> {
                String textToCopy = radioGroup.getCheckedRadioButtonId() == 1 ? jsonExport : csvExport;
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("主题", textToCopy);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "主题已复制到剪贴板", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private int parseColorFromValue(String value) {
        value = value.trim();
        if (value.startsWith("#")) {
            try {
                return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
            } catch (NumberFormatException e) {
                return currentScheme[0];
            }
        }
        return currentScheme[0];
    }

    private int parseColorFromJsonValue(Object value) {
        if (value instanceof Number) {
            int color = ((Number) value).intValue();
            if ((color & 0xFF000000) == 0) {
                color |= 0xFF000000;
            }
            return color;
        }
        if (value instanceof String) {
            return parseColorFromValue((String) value);
        }
        return currentScheme[0];
    }

    private Integer parseColorSilent(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            int color = ((Number) value).intValue();
            if ((color & 0xFF000000) == 0) {
                color |= 0xFF000000;
            }
            return color;
        }
        if (value instanceof String) {
            String v = ((String) value).trim();
            if (v.isEmpty()) return null;
            if (!v.startsWith("#")) {
                v = "#" + v;
            }
            try {
                return Color.parseColor(v);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer parseColorInput(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.isEmpty()) return null;
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        try {
            return Color.parseColor(value);
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject buildThemeJsonFromPalette(int[] colors, String name) {
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
            obj.put("name", name == null ? "Custom" : name);
            obj.put("type", "dark");
            obj.put("tags", new JSONArray());
            long now = System.currentTimeMillis();
            long createdAt = terminalPrefs.getLong(PREF_THEME_CREATED_AT, 0);
            if (createdAt == 0) {
                createdAt = now;
                terminalPrefs.edit().putLong(PREF_THEME_CREATED_AT, createdAt).apply();
            }
            obj.put("createdAt", createdAt);
            obj.put("updatedAt", now);
            obj.put("colors", base);
            obj.put("ansi", ansi);
            obj.put("cursor", buildCursorJson());
            obj.put("font", buildFontJson());
            obj.put("ui", buildUiJson());
            obj.put("behavior", buildBehaviorJson());
            obj.put("advanced", buildAdvancedJson());
        } catch (JSONException ignored) {
        }
        return obj;
    }

    private JSONObject normalizeThemeJsonObject(JSONObject obj) {
        long now = System.currentTimeMillis();
        try {
            if (!obj.has("version")) obj.put("version", 1);
            if (!obj.has("id")) obj.put("id", getOrCreateThemeId());
            if (!obj.has("name")) obj.put("name", "Custom");
            if (!obj.has("type")) obj.put("type", "dark");
            if (!obj.has("tags")) obj.put("tags", new JSONArray());
            long createdAt = obj.optLong("createdAt", 0);
            if (createdAt == 0) {
                createdAt = terminalPrefs.getLong(PREF_THEME_CREATED_AT, 0);
                if (createdAt == 0) {
                    createdAt = now;
                }
                obj.put("createdAt", createdAt);
                terminalPrefs.edit().putLong(PREF_THEME_CREATED_AT, createdAt).apply();
            }
            obj.put("updatedAt", now);
        } catch (JSONException ignored) {
        }
        return obj;
    }

    private String getOrCreateThemeId() {
        String id = terminalPrefs.getString(PREF_THEME_ID, null);
        if (id == null || id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString();
            terminalPrefs.edit().putString(PREF_THEME_ID, id).apply();
        }
        return id;
    }

    private JSONObject buildCursorJson() {
        JSONObject cursor = new JSONObject();
        try {
            int styleIndex = terminalPrefs.getInt(PREF_CURSOR_STYLE, 0);
            String style = styleIndex == TerminalView.CursorStyle.UNDERLINE.ordinal() ? "underline"
                : styleIndex == TerminalView.CursorStyle.BAR.ordinal() ? "beam" : "block";
            cursor.put("style", style);
            cursor.put("blink", terminalPrefs.getBoolean(PREF_CURSOR_BLINK, true));
            cursor.put("blinkIntervalMs", 500);
            cursor.put("color", formatColor(terminalPrefs.getInt(PREF_CURSOR_COLOR, 0xFFFFFFFF)));
            JSONObject vim = new JSONObject();
            vim.put("insert", new JSONObject().put("style", style));
            vim.put("normal", new JSONObject().put("style", "block"));
            vim.put("visual", new JSONObject().put("style", "underline"));
            cursor.put("vimMode", vim);
        } catch (JSONException ignored) {
        }
        return cursor;
    }

    private JSONObject buildFontJson() {
        JSONObject font = new JSONObject();
        try {
            float density = getResources().getDisplayMetrics().scaledDensity;
            int sizePx = terminalPrefs.getInt(PREF_FONT_SIZE_PX, Math.round(14 * density));
            float sizeSp = density == 0 ? 14f : sizePx / density;
            font.put("sizeSp", Math.round(sizeSp * 10f) / 10f);
            font.put("lineHeight", Math.round(currentLineHeight * 100f) / 100f);
            font.put("letterSpacing", Math.round(currentLetterSpacing * 100f) / 100f);
            font.put("family", currentFontFamily == 0 ? "monospace" : "system");
            font.put("boldWeight", currentFontWeight);
        } catch (JSONException ignored) {
        }
        return font;
    }

    private JSONObject buildUiJson() {
        JSONObject ui = new JSONObject();
        try {
            JSONObject tab = new JSONObject();
            tab.put("style", "underline");
            tab.put("color", "#444444");
            tab.put("activeColor", "#00BCD4");
            ui.put("tab", tab);
        } catch (JSONException ignored) {
        }
        return ui;
    }

    private JSONObject buildBehaviorJson() {
        JSONObject behavior = new JSONObject();
        try {
            JSONObject bell = new JSONObject();
            bell.put("audio", false);
            bell.put("visual", true);
            behavior.put("bell", bell);
        } catch (JSONException ignored) {
        }
        return behavior;
    }

    private JSONObject buildAdvancedJson() {
        JSONObject advanced = new JSONObject();
        try {
            float opacity = Math.max(0f, Math.min(1f, terminalBackgroundAlpha / 255f));
            advanced.put("backgroundOpacity", Math.round(opacity * 100f) / 100f);
            advanced.put("blur", 0);
            JSONObject root = new JSONObject();
            root.put("enabled", true);
            root.put("color", "#FF5252");
            advanced.put("rootHighlight", root);
            JSONObject danger = new JSONObject();
            danger.put("enabled", true);
            JSONArray patterns = new JSONArray();
            patterns.put("rm -rf");
            patterns.put("mkfs");
            patterns.put("dd if=");
            patterns.put(":(){:|:&};:");
            danger.put("patterns", patterns);
            advanced.put("dangerCommand", danger);
            JSONObject bindings = new JSONObject();
            bindings.put("hosts", new JSONArray());
            bindings.put("environments", new JSONArray().put("production").put("staging").put("dev"));
            advanced.put("bindings", bindings);
        } catch (JSONException ignored) {
        }
        return advanced;
    }

    private String formatColor(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    private void applyThemeJsonObject(JSONObject obj) {
        JSONObject colors = obj.optJSONObject("colors");
        if (colors != null) {
            Integer bg = parseColorSilent(colors.opt("background"));
            Integer fg = parseColorSilent(colors.opt("foreground"));
            if (bg != null || fg != null) {
                setCustomColors(bg != null ? bg : customBackgroundColor, fg != null ? fg : customForegroundColor);
            }
            Integer selection = parseColorSilent(colors.opt("selectionBackground"));
            if (selection != null) {
                currentSelectionColor = selection;
                terminalPrefs.edit().putInt(PREF_SELECTION_COLOR, currentSelectionColor).apply();
            }
            Integer search = parseColorSilent(colors.opt("searchHighlight"));
            if (search != null) {
                currentSearchHighlightColor = search;
                terminalPrefs.edit().putInt(PREF_SEARCH_HIGHLIGHT_COLOR, currentSearchHighlightColor).apply();
            }
        }
        int[] ansi = resolveAnsiScheme(obj);
        if (ansi != null) {
            currentScheme = ansi;
            saveCurrentColorScheme();
        }
        JSONObject cursor = obj.optJSONObject("cursor");
        if (cursor != null) {
            String style = cursor.optString("style", "");
            if (!style.isEmpty()) {
                TerminalView.CursorStyle s = parseCursorStyle(style);
                terminalPrefs.edit().putInt(PREF_CURSOR_STYLE, s.ordinal()).apply();
            }
            if (cursor.has("blink")) {
                terminalPrefs.edit().putBoolean(PREF_CURSOR_BLINK, cursor.optBoolean("blink", true)).apply();
            }
            Integer cursorColor = parseColorSilent(cursor.opt("color"));
            if (cursorColor != null) {
                terminalPrefs.edit().putInt(PREF_CURSOR_COLOR, cursorColor).apply();
            }
        }
        JSONObject font = obj.optJSONObject("font");
        if (font != null) {
            double sizeSp = font.optDouble("sizeSp", -1);
            if (sizeSp > 0) {
                float density = getResources().getDisplayMetrics().scaledDensity;
                int sizePx = Math.max(8, Math.round((float) sizeSp * density));
                terminalPrefs.edit().putInt(PREF_FONT_SIZE_PX, sizePx).apply();
            }
            double lineHeight = font.optDouble("lineHeight", -1);
            if (lineHeight > 0) {
                currentLineHeight = (float) lineHeight;
                terminalPrefs.edit().putFloat(PREF_LINE_HEIGHT, currentLineHeight).apply();
            }
            if (font.has("letterSpacing")) {
                currentLetterSpacing = (float) font.optDouble("letterSpacing", currentLetterSpacing);
                terminalPrefs.edit().putFloat(PREF_LETTER_SPACING, currentLetterSpacing).apply();
            }
            String family = font.optString("family", "");
            if (!family.isEmpty()) {
                currentFontFamily = "monospace".equalsIgnoreCase(family) ? 0 : 1;
                terminalPrefs.edit().putInt(PREF_FONT_FAMILY, currentFontFamily).apply();
            }
            int weight = font.optInt("boldWeight", currentFontWeight);
            if (weight > 0) {
                currentFontWeight = weight;
                terminalPrefs.edit().putInt(PREF_FONT_WEIGHT, currentFontWeight).apply();
            }
        }
        JSONObject advanced = obj.optJSONObject("advanced");
        if (advanced != null) {
            double opacity = advanced.optDouble("backgroundOpacity", -1);
            if (opacity >= 0 && opacity <= 1) {
                terminalBackgroundAlpha = Math.round((float) (opacity * 255f));
                terminalPrefs.edit().putInt(PREF_BG_ALPHA, terminalBackgroundAlpha).apply();
            }
        }
    }

    private TerminalView.CursorStyle parseCursorStyle(String style) {
        if ("underline".equalsIgnoreCase(style)) return TerminalView.CursorStyle.UNDERLINE;
        if ("beam".equalsIgnoreCase(style)) return TerminalView.CursorStyle.BAR;
        return TerminalView.CursorStyle.BLOCK;
    }

    private int[] resolveAnsiScheme(JSONObject obj) {
        JSONObject ansi = obj.optJSONObject("ansi");
        if (ansi == null) return null;
        JSONObject setObj = null;
        JSONObject sets = ansi.optJSONObject("sets");
        String active = ansi.optString("active", "");
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
        return parseAnsiSchemeFromObject(setObj);
    }

    private int[] parseAnsiSchemeFromObject(JSONObject ansi) {
        if (ansi == null) return null;
        JSONArray palette = ansi.optJSONArray("palette");
        if (palette != null && palette.length() >= 16) {
            int[] scheme = new int[16];
            for (int i = 0; i < 16; i++) {
                scheme[i] = parseColorFromJsonValue(palette.opt(i));
            }
            return scheme;
        }
        JSONObject standard = ansi.optJSONObject("standard");
        JSONObject bright = ansi.optJSONObject("bright");
        if (standard == null && bright == null) return null;
        int[] scheme = new int[16];
        scheme[0] = parseColorFromJsonValue(standard != null ? standard.opt("black") : null);
        scheme[1] = parseColorFromJsonValue(standard != null ? standard.opt("red") : null);
        scheme[2] = parseColorFromJsonValue(standard != null ? standard.opt("green") : null);
        scheme[3] = parseColorFromJsonValue(standard != null ? standard.opt("yellow") : null);
        scheme[4] = parseColorFromJsonValue(standard != null ? standard.opt("blue") : null);
        scheme[5] = parseColorFromJsonValue(standard != null ? standard.opt("magenta") : null);
        scheme[6] = parseColorFromJsonValue(standard != null ? standard.opt("cyan") : null);
        scheme[7] = parseColorFromJsonValue(standard != null ? standard.opt("white") : null);
        scheme[8] = parseColorFromJsonValue(bright != null ? bright.opt("brightBlack") : null);
        scheme[9] = parseColorFromJsonValue(bright != null ? bright.opt("brightRed") : null);
        scheme[10] = parseColorFromJsonValue(bright != null ? bright.opt("brightGreen") : null);
        scheme[11] = parseColorFromJsonValue(bright != null ? bright.opt("brightYellow") : null);
        scheme[12] = parseColorFromJsonValue(bright != null ? bright.opt("brightBlue") : null);
        scheme[13] = parseColorFromJsonValue(bright != null ? bright.opt("brightMagenta") : null);
        scheme[14] = parseColorFromJsonValue(bright != null ? bright.opt("brightCyan") : null);
        scheme[15] = parseColorFromJsonValue(bright != null ? bright.opt("brightWhite") : null);
        return scheme;
    }

    private void saveCurrentColorScheme() {
        SharedPreferences.Editor editor = terminalPrefs.edit();
        for (int i = 0; i < 16; i++) {
            editor.putInt(PREF_CUSTOM_COLOR_PREFIX + i, currentScheme[i]);
        }
        editor.putInt(PREF_CUSTOM_THEME_VERSION, 1);
        editor.apply();
    }

    private void setCustomColors(Integer bgColor, Integer fgColor) {
        customBackgroundColor = bgColor;
        customForegroundColor = fgColor;
        SharedPreferences.Editor editor = terminalPrefs.edit();
        if (bgColor != null) editor.putInt(PREF_CUSTOM_BG, bgColor);
        else editor.remove(PREF_CUSTOM_BG);
        if (fgColor != null) editor.putInt(PREF_CUSTOM_FG, fgColor);
        else editor.remove(PREF_CUSTOM_FG);
        editor.apply();
    }
}
