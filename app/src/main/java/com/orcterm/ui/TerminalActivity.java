package com.orcterm.ui;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.button.MaterialButton;
import com.orcterm.R;
import com.orcterm.core.terminal.TerminalSession;
import com.orcterm.util.PersistentNotificationHelper;
import com.orcterm.ui.widget.TerminalInputView;
import com.orcterm.ui.widget.TerminalKeypadView;
import com.orcterm.ui.widget.TerminalView;
import com.orcterm.util.PersistentNotificationHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 终端模拟 Activity
 * 应用程序的核心界面，负责管理终端容器、会话连接和用户交互。
 * 
 * 主要功能:
 * 1. 容器管理: 支持多标签页 (TerminalContainer)，可分组管理。
 * 2. 终端渲染: 使用 TerminalView 进行 ANSI 字符绘制。
 * 3. 输入处理: 通过 TerminalInputView 拦截软键盘输入，支持虚拟键盘 (Keypad)。
 * 4. 会话管理: 维护 SSH/Telnet/Local 会话生命周期。
 * 5. 状态持久化: 保存容器、分组和当前活动状态。
 */
public class TerminalActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "terminal_containers";
    private static final String PREF_GROUPS = "groups";
    private static final String PREF_CONTAINERS = "containers";
    private static final String PREF_ACTIVE_ID = "active_container_id";
    private static final String PREF_SELECTED_GROUP = "selected_group";
    private static final String PREF_APP = "orcterm_prefs";
    private static final String PREF_KEYPAD_VISIBLE = "terminal_keypad_visible";
    private static final String PREF_KEYPAD_MAPPING = "terminal_keypad_mapping";
    private static final String PREF_LINE_HEIGHT = "terminal_line_height";
    private static final String PREF_LETTER_SPACING = "terminal_letter_spacing";
    private static final String PREF_FONT_FAMILY = "terminal_font_family";
    private static final String PREF_BG_URI = "terminal_bg_uri";
    private static final String PREF_INITIAL_COMMAND_PREFIX = "terminal_initial_command_";
    private static final String PREF_BG_ALPHA = "terminal_bg_alpha";
    private static final String PREF_CUSTOM_ACTIONS = "terminal_custom_actions";
    private static final String PREF_CUSTOM_BG = "terminal_bg_color";
    private static final String PREF_CUSTOM_FG = "terminal_fg_color";
    private static final String PREF_ENTER_NEWLINE = "terminal_enter_newline";
    private static final String PREF_LOCAL_ECHO = "terminal_local_echo";

    // 默认配色方案 (标准 16 色)
    private static final int[] SCHEME_DEFAULT = {
        0xFF000000, 0xFFCD0000, 0xFF00CD00, 0xFFCDCD00, 0xFF0000EE, 0xFFCD00CD, 0xFF00CDCD, 0xFFE5E5E5,
        0xFF7F7F7F, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF5C5CFF, 0xFFFF00FF, 0xFF00FFFF, 0xFFFFFFFF
    };
    // Solarized Dark 配色
    private static final int[] SCHEME_SOLARIZED_DARK = {
        0xFF002B36, 0xFFDC322F, 0xFF859900, 0xFFB58900, 0xFF268BD2, 0xFFD33682, 0xFF2AA198, 0xFFEEE8D5,
        0xFF073642, 0xFFCB4B16, 0xFF586E75, 0xFF657B83, 0xFF839496, 0xFF6C71C4, 0xFF93A1A1, 0xFFFDF6E3
    };
    // Solarized Light 配色
    private static final int[] SCHEME_SOLARIZED_LIGHT = {
        0xFFFDF6E3, 0xFFDC322F, 0xFF859900, 0xFFB58900, 0xFF268BD2, 0xFFD33682, 0xFF2AA198, 0xFF073642,
        0xFFEEE8D5, 0xFFCB4B16, 0xFF586E75, 0xFF657B83, 0xFF839496, 0xFF6C71C4, 0xFF93A1A1, 0xFF002B36
    };
    private static final int[] SCHEME_MONOKAI = {
        0xFF272822, 0xFFF92672, 0xFFA6E22E, 0xFFE6DB74, 0xFF66D9EF, 0xFFAE81FF, 0xFF38CCD1, 0xFFF8F8F2,
        0xFF75715E, 0xFFF92672, 0xFFA6E22E, 0xFFE6DB74, 0xFF66D9EF, 0xFFAE81FF, 0xFF38CCD1, 0xFFF9F8F5
    };

    // 预设主题
    private static final int[] THEME_DRACULA = {
        0xFF1E1F28, 0xFFFF5555, 0xFF50FA7B, 0xFFF1FA8C,
        0xFFBD93F9, 0xFFFF79C6, 0xFF8BE9FD, 0xFFF8F8F2,
        0xFF6272A4, 0xFFFF6E6E, 0xFF69FF94, 0xFFFFFFA5,
        0xFFD6ACFF, 0xFFFF92DF, 0xFFA4FFFF, 0xFFFFFFFF
    };

    private static final int[] THEME_NORD = {
        0xFF2E3440, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B,
        0xFF81A1C1, 0xFFB48EAD, 0xFF88C0D0, 0xFFECEFF4,
        0xFF4C566A, 0xFFD08770, 0xFFBF616A, 0xFFEBCB8B,
        0xFF81A1C1, 0xFFB48EAD, 0xFF8FBCBB, 0xFFECEFF4
    };

    private static final int[] THEME_GRUVBOX = {
        0xFF282828, 0xFFCC241D, 0xFF98971A, 0xFFD79921,
        0xFF458588, 0xFFB16286, 0xFF689D6A, 0xFFA89984,
        0xFF928374, 0xFFFB4934, 0xFFB8BB26, 0xFFFABD2F,
        0xFF83A598, 0xFFD3869B, 0xFF8EC07C, 0xFFEBDBB2
    };
    
    private int currentFontSize = 36;
    private int[] currentScheme = SCHEME_DEFAULT;
    private float currentLineHeight = 1.0f;
    private float currentLetterSpacing = 0.0f;
    private int currentFontFamily = 0;
    private Bitmap terminalBackground;
    private int terminalBackgroundAlpha = 255;
    private boolean keypadVisible = true;
    private Integer customBackgroundColor;
    private Integer customForegroundColor;
    private boolean enterNewline = true;
    private boolean localEcho = false;

    // UI 组件
    private FrameLayout containerHost;
    private RecyclerView containerList;
    private MaterialButton btnAddContainer;
    private TerminalInputView inputCommand;
    private TerminalKeypadView keypadView;

    // 数据模型
    private final List<TerminalContainer> containers = new ArrayList<>();
    private final List<TerminalContainer> visibleContainers = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();

    private ContainerAdapter containerAdapter;
    private String currentGroup;
    private TerminalContainer activeContainer;
    private long nextContainerId = 1;
    private TerminalContainer splitSecondary;
    private android.widget.LinearLayout splitLayout;
    private FrameLayout splitPrimaryHost;
    private FrameLayout splitSecondaryHost;
    private boolean splitMode = false;
    private boolean splitVertical = false;
    private float splitRatio = 0.5f;
    private View splitDivider;
    private static final String PREF_SPLIT_MODE = "terminal_split_mode";
    private static final String PREF_SPLIT_VERTICAL = "terminal_split_vertical";
    private static final String PREF_SPLIT_RATIO = "terminal_split_ratio";
    private SharedPreferences terminalPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private final Map<String, String> keypadMapping = new HashMap<>();
    private final List<TerminalAction> defaultActions = new ArrayList<>();
    private final List<TerminalAction> customActions = new ArrayList<>();
    private ActivityResultLauncher<String> terminalBgPicker;

    // 连接参数 (从 Intent 获取)
    private String hostname;
    private String username;
    private int port;
    private String password;
    private int authType;
    private String keyPath;
    private String initialCommand;
    private boolean initialCommandSent = false;

    // 键盘修饰键状态
    private boolean isCtrlPressed = false;
    private boolean isAltPressed = false;

    /**
     * 将 Android 按键映射为 ANSI 转义序列
     *
     * @param keyCode 按键代码
     * @param event   按键事件
     * @return ANSI 序列字符串，如果无映射则返回 null
     */
    private String getAnsiSequence(int keyCode, KeyEvent event) {
        // 数字键盘映射
        String numpadSeq = getNumpadSequence(event);
        if (numpadSeq != null) {
            return numpadSeq;
        }

        // 方向键映射
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_MOVE_END) {
            return getModifiedArrowSequence("A", event);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            return getModifiedArrowSequence("B", event);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return getModifiedArrowSequence("C", event);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return getModifiedArrowSequence("D", event);
        }

        // 编辑键映射
        if (keyCode == KeyEvent.KEYCODE_MOVE_HOME) {
            return event.isCtrlPressed() ? "\033[1;5H" : "\033[H";
        }
        if (keyCode == KeyEvent.KEYCODE_MOVE_END) {
            return event.isCtrlPressed() ? "\033[1;5F" : "\033[F";
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            return event.isCtrlPressed() ? "\033[5;5~" : "\033[5~";
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            return event.isCtrlPressed() ? "\033[6;5~" : "\033[6~";
        }
        if (keyCode == KeyEvent.KEYCODE_INSERT) {
            return event.isCtrlPressed() ? "\033[2;5~" : "\033[2~";
        }
        if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            return event.isCtrlPressed() ? "\033[3;5~" : "\033[3~";
        }

        // 功能键 F1-F20
        String fkeySeq = getFunctionKeySequence(keyCode, event);
        if (fkeySeq != null) {
            return fkeySeq;
        }

        // 处理 Ctrl+Key 组合 (如果 Keypad 未处理)
        if (event.isCtrlPressed()) {
             int uchar = event.getUnicodeChar(event.getMetaState() & ~KeyEvent.META_CTRL_MASK);
             if (uchar >= 'a' && uchar <= 'z') {
                 // 转换 'a' (97) -> 1 (Ctrl-A)
                 return String.valueOf((char)(uchar - 'a' + 1));
             }
        }

        // 处理 Alt+Key 组合
        if (event.isAltPressed()) {
             int uchar = event.getUnicodeChar();
             if (uchar >= 32 && uchar <= 126) {
                 return "\033" + String.valueOf((char)uchar);
             }
        }

        return null;
    }

    /**
     * 获取修饰键+方向键的 ANSI 序列
     */
    private String getModifiedArrowSequence(String baseSeq, KeyEvent event) {
        if (event.isCtrlPressed()) {
            return "\033[1;5" + baseSeq;
        } else if (event.isAltPressed()) {
            return "\033[1;3" + baseSeq;
        } else if (event.isShiftPressed()) {
            return "\033[1;2" + baseSeq;
        } else {
            return "\033[" + baseSeq;
        }
    }

    /**
     * 获取数字键盘的 ANSI 序列
     */
    private String getNumpadSequence(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // 检查是否是数字键盘按键
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return String.valueOf(keyCode - KeyEvent.KEYCODE_NUMPAD_0);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return "\r";
            case KeyEvent.KEYCODE_NUMPAD_EQUALS:
                return "=";
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE:
                return "/";
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
                return "*";
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                return "-";
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                return "+";
            case KeyEvent.KEYCODE_NUMPAD_DOT:
                return ".";
            case KeyEvent.KEYCODE_NUMPAD_COMMA:
                return ",";
            default:
                return null;
        }
    }

    /**
     * 获取功能键的 ANSI 序列
     */
    private String getFunctionKeySequence(int keyCode, KeyEvent event) {
        String modifier = "";
        if (event.isCtrlPressed()) {
            modifier = ";5";
        } else if (event.isAltPressed()) {
            modifier = ";3";
        } else if (event.isShiftPressed()) {
            modifier = ";2";
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_F1: return "\033OP";
            case KeyEvent.KEYCODE_F2: return "\033OQ";
            case KeyEvent.KEYCODE_F3: return "\033OR";
            case KeyEvent.KEYCODE_F4: return "\033OS";
            case KeyEvent.KEYCODE_F5: return "\033[15" + modifier + "~";
            case KeyEvent.KEYCODE_F6: return "\033[17" + modifier + "~";
            case KeyEvent.KEYCODE_F7: return "\033[18" + modifier + "~";
            case KeyEvent.KEYCODE_F8: return "\033[19" + modifier + "~";
            case KeyEvent.KEYCODE_F9: return "\033[20" + modifier + "~";
            case KeyEvent.KEYCODE_F10: return "\033[21" + modifier + "~";
            case KeyEvent.KEYCODE_F11: return "\033[23" + modifier + "~";
            case KeyEvent.KEYCODE_F12: return "\033[24" + modifier + "~";
            // F13-F20 映射 (如果设备支持)
            /*case KeyEvent.KEYCODE_F13: return "\033[25" + modifier + "~";
            case KeyEvent.KEYCODE_F14: return "\033[26" + modifier + "~";
            case KeyEvent.KEYCODE_F15: return "\033[28" + modifier + "~";
            case KeyEvent.KEYCODE_F16: return "\033[29" + modifier + "~";
            case KeyEvent.KEYCODE_F17: return "\033[31" + modifier + "~";
            case KeyEvent.KEYCODE_F18: return "\033[32" + modifier + "~";
            case KeyEvent.KEYCODE_F19: return "\033[33" + modifier + "~";
            case KeyEvent.KEYCODE_F20: return "\033[34" + modifier + "~";*/
            default: return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        // 适配系统窗口插图 (Safe Area)，确保底部虚拟键盘不被系统导航栏遮挡
        View root = findViewById(android.R.id.content);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            findViewById(R.id.keypad_view).setPadding(0, 0, 0, bottom);
            return insets;
        });

        // 初始化视图
        containerHost = findViewById(R.id.container_host);
        containerList = findViewById(R.id.container_list);
        btnAddContainer = findViewById(R.id.btn_add_container);
        inputCommand = findViewById(R.id.input_command);
        keypadView = findViewById(R.id.keypad_view);
        terminalPrefs = getSharedPreferences(PREF_APP, MODE_PRIVATE);
        int themeIndex = terminalPrefs.getInt("terminal_theme_index", 0);
        if (themeIndex == 1) currentScheme = SCHEME_SOLARIZED_DARK;
        else if (themeIndex == 2) currentScheme = SCHEME_SOLARIZED_LIGHT;
        else if (themeIndex == 3) currentScheme = SCHEME_MONOKAI;
        int fontIndex = terminalPrefs.getInt("font_size_index", 1);
        if (fontIndex == 0) currentFontSize = 24;
        else if (fontIndex == 1) currentFontSize = 36;
        else if (fontIndex == 2) currentFontSize = 48;
        else if (fontIndex == 3) currentFontSize = 60;
        currentLineHeight = terminalPrefs.getFloat(PREF_LINE_HEIGHT, 1.0f);
        currentLetterSpacing = terminalPrefs.getFloat(PREF_LETTER_SPACING, 0.0f);
        currentFontFamily = terminalPrefs.getInt(PREF_FONT_FAMILY, 0);
        terminalBackgroundAlpha = terminalPrefs.getInt(PREF_BG_ALPHA, 255);
        enterNewline = terminalPrefs.getBoolean(PREF_ENTER_NEWLINE, true);
        localEcho = terminalPrefs.getBoolean(PREF_LOCAL_ECHO, false);
        if (terminalPrefs.contains(PREF_CUSTOM_BG)) {
            customBackgroundColor = terminalPrefs.getInt(PREF_CUSTOM_BG, 0xFF000000);
        }
        if (terminalPrefs.contains(PREF_CUSTOM_FG)) {
            customForegroundColor = terminalPrefs.getInt(PREF_CUSTOM_FG, 0xFFFFFFFF);
        }
        String bgUriStr = terminalPrefs.getString(PREF_BG_URI, null);
        if (bgUriStr != null) {
            try {
                Uri uri = Uri.parse(bgUriStr);
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in != null) {
                        terminalBackground = BitmapFactory.decodeStream(in);
                    }
                }
            } catch (Exception ignored) {}
        }
        keypadVisible = terminalPrefs.getBoolean(PREF_KEYPAD_VISIBLE, true);
        keypadView.setVisibility(keypadVisible ? View.VISIBLE : View.GONE);
        loadKeypadMappingFromPrefs();
        keypadView.setCustomMapping(keypadMapping);
        keypadView.setLongClickListener((label, value) -> {
            EditText edit = new EditText(this);
            edit.setText(keypadMapping.containsKey(label) ? keypadMapping.get(label) : value);
            new AlertDialog.Builder(this)
                .setTitle("映射: " + label)
                .setView(edit)
                .setPositiveButton("保存", (d, w) -> {
                    String v = edit.getText().toString();
                    keypadMapping.put(label, v);
                    keypadView.setCustomMapping(keypadMapping);
                    persistKeypadMapping();
                })
                .setNegativeButton("取消", null)
                .show();
        });
        terminalBgPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in != null) {
                        terminalBackground = BitmapFactory.decodeStream(in);
                        terminalPrefs.edit().putString(PREF_BG_URI, uri.toString()).apply();
                        applyDisplayPreferencesToAllViews();
                        Toast.makeText(this, "背景已设置", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "背景加载失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        initDefaultActions();
        loadCustomActions();

        // 获取连接参数
        hostname = getIntent().getStringExtra("hostname");
        username = getIntent().getStringExtra("username");
        port = getIntent().getIntExtra("port", 22);
        password = getIntent().getStringExtra("password");
        authType = getIntent().getIntExtra("auth_type", 0);
        keyPath = getIntent().getStringExtra("key_path");
        initialCommand = getIntent().getStringExtra("initial_command");
        if (initialCommand == null) {
            initialCommand = getIntent().getStringExtra("initialCommand");
        }
        if (initialCommand != null && initialCommand.trim().isEmpty()) {
            initialCommand = null;
        }
        String initialCommandKey = getInitialCommandCacheKey();
        if (initialCommand != null && initialCommandKey != null) {
            terminalPrefs.edit().putString(initialCommandKey, initialCommand).apply();
        } else if (initialCommand == null && initialCommandKey != null) {
            String cached = terminalPrefs.getString(initialCommandKey, null);
            if (cached != null && !cached.trim().isEmpty()) {
                initialCommand = cached;
            }
        }

        // 设置容器列表适配器
        containerList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        containerAdapter = new ContainerAdapter();
        containerList.setAdapter(containerAdapter);
        setupItemTouchHelper();

        // 绑定按钮事件
        btnAddContainer.setOnClickListener(v -> showCreateContainerDialog());

        // 设置输入监听器
        inputCommand.setOnKeyInputListener(new TerminalInputView.OnKeyInputListener() {
            @Override
            public void onInput(String text) {
                dispatchInput(text);
            }

            @Override
            public void onKeyDown(int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        dispatchInput("\r");
                    } else {
                        String seq = getAnsiSequence(keyCode, event);
                        if (seq != null) {
                            dispatchInput(seq);
                        }
                    }
                }
            }
        });

        // 设置虚拟键盘监听器
        keypadView.setListener((label, value) -> {
            if (activeContainer == null) {
                return;
            }
            if ("CTRL".equals(value)) {
                isCtrlPressed = !isCtrlPressed;
                Toast.makeText(this, "CTRL " + (isCtrlPressed ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            } else if ("ALT".equals(value)) {
                isAltPressed = !isAltPressed;
                Toast.makeText(this, "ALT " + (isAltPressed ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            } else {
                if (isCtrlPressed && value.length() == 1) {
                    // 处理虚拟 CTRL 组合键
                    char c = value.charAt(0);
                    if (c >= 'a' && c <= 'z') {
                        char ctrlChar = (char) (c - 'a' + 1);
                        dispatchInput(String.valueOf(ctrlChar));
                    } else if (c >= 'A' && c <= 'Z') {
                        char ctrlChar = (char) (c - 'A' + 1);
                        dispatchInput(String.valueOf(ctrlChar));
                    } else {
                        dispatchInput(value);
                    }
                    isCtrlPressed = false;
                } else {
                    dispatchInput(value);
                }
            }
        });

        loadState();
        refreshVisibleContainers();
        applyDisplayPreferencesToAllViews();
        splitMode = terminalPrefs.getBoolean(PREF_SPLIT_MODE, false);
        splitVertical = terminalPrefs.getBoolean(PREF_SPLIT_VERTICAL, false);
        splitRatio = terminalPrefs.getFloat(PREF_SPLIT_RATIO, 0.5f);
        if (splitMode) {
            applySplitLayout();
        }
        prefListener = (prefs, key) -> {
            if ("terminal_theme_index".equals(key)) {
                int idx = prefs.getInt("terminal_theme_index", 0);
                if (idx == 1) applyColorScheme(SCHEME_SOLARIZED_DARK);
                else if (idx == 2) applyColorScheme(SCHEME_SOLARIZED_LIGHT);
                else if (idx == 3) applyColorScheme(SCHEME_MONOKAI);
                else applyColorScheme(SCHEME_DEFAULT);
            } else if ("font_size_index".equals(key)) {
                int which = prefs.getInt("font_size_index", 1);
                int size = which == 0 ? 24 : which == 1 ? 36 : which == 2 ? 48 : 60;
                applyFontSize(size);
            } else if (PREF_LINE_HEIGHT.equals(key) || PREF_LETTER_SPACING.equals(key) || PREF_FONT_FAMILY.equals(key) || PREF_BG_ALPHA.equals(key) || PREF_BG_URI.equals(key) || PREF_CUSTOM_BG.equals(key) || PREF_CUSTOM_FG.equals(key)) {
                currentLineHeight = prefs.getFloat(PREF_LINE_HEIGHT, currentLineHeight);
                currentLetterSpacing = prefs.getFloat(PREF_LETTER_SPACING, currentLetterSpacing);
                currentFontFamily = prefs.getInt(PREF_FONT_FAMILY, currentFontFamily);
                terminalBackgroundAlpha = prefs.getInt(PREF_BG_ALPHA, terminalBackgroundAlpha);
                String uriStr = prefs.getString(PREF_BG_URI, null);
                if (uriStr != null) {
                    try {
                        Uri uri = Uri.parse(uriStr);
                        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                            if (in != null) terminalBackground = BitmapFactory.decodeStream(in);
                        }
                    } catch (Exception ignored) {}
                } else {
                    terminalBackground = null;
                }
                if (prefs.contains(PREF_CUSTOM_BG)) customBackgroundColor = prefs.getInt(PREF_CUSTOM_BG, 0xFF000000);
                else customBackgroundColor = null;
                if (prefs.contains(PREF_CUSTOM_FG)) customForegroundColor = prefs.getInt(PREF_CUSTOM_FG, 0xFFFFFFFF);
                else customForegroundColor = null;
                applyDisplayPreferencesToAllViews();
            } else if (PREF_KEYPAD_MAPPING.equals(key)) {
                loadKeypadMappingFromPrefs();
                keypadView.setCustomMapping(keypadMapping);
            } else if (PREF_ENTER_NEWLINE.equals(key) || PREF_LOCAL_ECHO.equals(key)) {
                enterNewline = prefs.getBoolean(PREF_ENTER_NEWLINE, enterNewline);
                localEcho = prefs.getBoolean(PREF_LOCAL_ECHO, localEcho);
            }
        };
        terminalPrefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    /**
     * 处理物理键盘快捷键
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // Ctrl+Tab: 切换下一个容器
            if (event.isCtrlPressed() && event.getKeyCode() == KeyEvent.KEYCODE_TAB) {
                if (event.isShiftPressed()) {
                    switchToPreviousContainer();
                } else {
                    switchToNextContainer();
                }
                return true;
            }
            // Alt+N: 新建容器
            if (event.isAltPressed() && event.getKeyCode() == KeyEvent.KEYCODE_N) {
                showCreateContainerDialog();
                return true;
            }
            // Alt+W: 关闭容器
            if (event.isAltPressed() && event.getKeyCode() == KeyEvent.KEYCODE_W) {
                if (activeContainer != null) {
                    closeContainer(activeContainer);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void refreshVisibleContainers() {
        visibleContainers.clear();
        List<TerminalContainer> pinned = new ArrayList<>();
        List<TerminalContainer> normal = new ArrayList<>();
        for (TerminalContainer container : containers) {
            if (container.pinned) {
                pinned.add(container);
            } else {
                normal.add(container);
            }
        }
        visibleContainers.addAll(pinned);
        visibleContainers.addAll(normal);
        containerAdapter.notifyDataSetChanged();
        if (visibleContainers.isEmpty()) {
            // 如果分组为空，自动创建一个默认容器
            TerminalContainer created = createContainerInternal(defaultContainerName(), currentGroup, nextContainerId++);
            setActiveContainer(created);
            refreshVisibleContainers();
            return;
        }
        if (activeContainer == null || !activeContainer.group.equals(currentGroup)) {
            setActiveContainer(visibleContainers.get(0));
        }
    }

    private String defaultContainerName() {
        if (!TextUtils.isEmpty(hostname)) {
            return hostname;
        }
        return "容器 " + (containers.size() + 1);
    }

    private String getInitialCommandCacheKey() {
        if (TextUtils.isEmpty(hostname) || TextUtils.isEmpty(username)) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(PREF_INITIAL_COMMAND_PREFIX);
        sb.append(hostname).append('|').append(port).append('|').append(username).append('|').append(authType).append('|');
        if (authType == 1 && keyPath != null) {
            sb.append(keyPath);
        }
        return sb.toString();
    }

    // --- 对话框辅助方法 ---

    private void showCreateContainerDialog() {
        String name = defaultContainerName();
        TerminalContainer container = createContainerInternal(name, currentGroup, nextContainerId++);
        refreshVisibleContainers();
        setActiveContainer(container);
    }

    private void showRenameContainerDialog(TerminalContainer container) {
        EditText input = new EditText(this);
        input.setText(container.name);
        new AlertDialog.Builder(this)
                .setTitle("重命名容器")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        container.name = name;
                        refreshVisibleContainers();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 内部创建容器方法
     * 初始化 Session 和 View，并添加到界面。
     */
    private TerminalContainer createContainerInternal(String name, String group, long id) {
        TerminalContainer container = new TerminalContainer();
        container.id = id;
        container.name = name;
        container.group = group;

        // 初始化会话
        TerminalSession session = new TerminalSession();
        session.setListener(new ContainerSessionListener(container));
        session.connect(hostname, port, username, password, authType, keyPath);
        container.session = session;

        // 初始化视图
        TerminalView view = new TerminalView(this);
        view.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        view.setFontSize(currentFontSize);
        view.setColorScheme(getEffectiveScheme());
        view.setOnResizeListener((cols, rows) -> container.session.resize(cols, rows));
        view.setLineHeightMultiplier(currentLineHeight);
        view.setLetterSpacing(currentLetterSpacing);
        Typeface tf = currentFontFamily == 0 ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
        view.setTypeface(tf);
        if (terminalBackground != null) {
            view.setBackgroundImage(terminalBackground);
            view.setBackgroundAlpha(terminalBackgroundAlpha);
        }
        
        // 应用光标设置
        applyCursorSettings(view);
        applyBackgroundSettings(view);
        
        // 点击终端视图应聚焦输入框并显示软键盘
        view.setOnClickListener(v -> {
            setActiveContainer(container);
            inputCommand.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(inputCommand, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        
        view.setVisibility(View.GONE);
        container.view = view;
        containerHost.addView(view);

        containers.add(container);
        return container;
    }

    private void closeContainer(TerminalContainer container) {
        containers.remove(container);
        visibleContainers.remove(container);
        containerHost.removeView(container.view);
        if (container.session != null) {
            container.session.disconnect();
        }
        if (splitMode && (container == activeContainer || container == splitSecondary)) {
            disableSplitMode();
        }
        if (container == activeContainer) {
            activeContainer = null;
        }
        refreshVisibleContainers();
    }

    private void switchToNextContainer() {
        if (visibleContainers.isEmpty()) {
            return;
        }
        int index = visibleContainers.indexOf(activeContainer);
        int nextIndex = (index + 1) % visibleContainers.size();
        setActiveContainer(visibleContainers.get(nextIndex));
    }

    private void switchToPreviousContainer() {
        if (visibleContainers.isEmpty()) {
            return;
        }
        int index = visibleContainers.indexOf(activeContainer);
        int prevIndex = index <= 0 ? visibleContainers.size() - 1 : index - 1;
        setActiveContainer(visibleContainers.get(prevIndex));
    }

    private void setActiveContainer(TerminalContainer container) {
        activeContainer = container;
        if (splitMode) {
            applySplitLayout();
        } else {
            for (TerminalContainer item : containers) {
                item.view.setVisibility(item == container ? View.VISIBLE : View.GONE);
            }
        }
        containerAdapter.notifyDataSetChanged();
        maybeRunInitialCommand(container);
        
        // 切换容器时确保输入框获得焦点
        if (inputCommand != null) {
            inputCommand.requestFocus();
        }
    }

    private void maybeRunInitialCommand(TerminalContainer container) {
        if (initialCommand == null || initialCommandSent) return;
        if (container == null || container != activeContainer) return;
        if (!container.connected) return;
        initialCommandSent = true;
        sendCommand(initialCommand);
    }

    private TerminalSession getActiveSession() {
        return activeContainer != null ? activeContainer.session : null;
    }

    private String normalizeLineEnding(String text) {
        if (text == null) return "";
        if (enterNewline) {
            return text.replace("\r", "\n");
        }
        return text.replace("\n", "\r");
    }

    private void dispatchInput(String text) {
        if (text == null || text.isEmpty()) return;
        TerminalContainer container = activeContainer;
        if (container == null) return;
        String send = normalizeLineEnding(text);
        if (localEcho && container.view != null) {
            container.view.append(send);
        }
        if (container.session != null) {
            container.session.write(send);
        }
    }

    // --- 状态保存与恢复 ---

    private void loadState() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String groupsJson = prefs.getString(PREF_GROUPS, null);
        if (groupsJson != null) {
            try {
                JSONArray array = new JSONArray(groupsJson);
                for (int i = 0; i < array.length(); i++) {
                    String name = array.optString(i);
                    if (!name.isEmpty()) {
                        groups.add(name);
                    }
                }
            } catch (JSONException ignored) {
            }
        }
        if (groups.isEmpty()) {
            groups.add("默认");
        }

        currentGroup = prefs.getString(PREF_SELECTED_GROUP, groups.get(0));

        String containersJson = prefs.getString(PREF_CONTAINERS, null);
        if (containersJson != null) {
            try {
                JSONArray array = new JSONArray(containersJson);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    long id = obj.optLong("id");
                    String name = obj.optString("name", defaultContainerName());
                    String group = obj.optString("group", groups.get(0));
                    boolean pinned = obj.optBoolean("pinned", false);
                    createContainerInternal(name, group, id);
                    if (!containers.isEmpty()) {
                        containers.get(containers.size() - 1).pinned = pinned;
                    }
                    if (id >= nextContainerId) {
                        nextContainerId = id + 1;
                    }
                }
            } catch (JSONException ignored) {
            }
        }

        if (containers.isEmpty()) {
            createContainerInternal(defaultContainerName(), groups.get(0), nextContainerId++);
        }

        long activeId = prefs.getLong(PREF_ACTIVE_ID, -1L);
        for (TerminalContainer container : containers) {
            if (container.id == activeId) {
                setActiveContainer(container);
                break;
            }
        }
    }

    private void persistState() {
        JSONArray groupArray = new JSONArray();
        for (String group : groups) {
            groupArray.put(group);
        }
        JSONArray containerArray = new JSONArray();
        for (TerminalContainer container : containers) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", container.id);
                obj.put("name", container.name);
                obj.put("group", container.group);
                obj.put("pinned", container.pinned);
            } catch (JSONException ignored) {
            }
            containerArray.put(obj);
        }
        android.content.SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(PREF_GROUPS, groupArray.toString());
        editor.putString(PREF_CONTAINERS, containerArray.toString());
        editor.putLong(PREF_ACTIVE_ID, activeContainer != null ? activeContainer.id : -1L);
        editor.putString(PREF_SELECTED_GROUP, currentGroup);
        editor.apply();
    }

    @Override
    protected void onStop() {
        super.onStop();
        persistState();
        // 重置虚拟键盘的修饰键状态
        if (keypadView != null) {
            keypadView.resetModifiers();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (TerminalContainer container : containers) {
            if (container.session != null) {
                container.session.disconnect();
            }
        }
        if (prefListener != null) {
            terminalPrefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }

    // --- 菜单与选项 ---

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1001, 0, "字体: 小");
        menu.add(0, 1002, 0, "字体: 中");
        menu.add(0, 1003, 0, "字体: 大");
        menu.add(0, 1004, 0, "字体: 特大");
        menu.add(0, 1005, 0, "字体: 等宽");
        menu.add(0, 1006, 0, "字体: 系统");
        menu.add(0, 2001, 0, "配色: 默认");
        menu.add(0, 2002, 0, "配色: Solarized Dark");
        menu.add(0, 2003, 0, "配色: Solarized Light");
        menu.add(0, 2004, 0, "配色: Monokai");
        menu.add(0, 2005, 0, "配色: Dracula");
        menu.add(0, 2006, 0, "配色: Nord");
        menu.add(0, 2007, 0, "配色: Gruvbox");
        menu.add(0, 2010, 0, "主题编辑器...");
        menu.add(0, 2011, 0, "导入主题...");
        menu.add(0, 2012, 0, "导出主题...");
        menu.add(0, 3002, 0, "键盘: 显示/隐藏");
        menu.add(0, 5001, 0, "命令面板...");
        menu.add(0, 5002, 0, "添加命令...");
        menu.add(0, 7001, 0, "搜索...");
        menu.add(0, 7002, 0, "清除搜索");
        menu.add(0, 8001, 0, "分屏: 开/关");
        menu.add(0, 8002, 0, "分屏: 选择窗口");
        menu.add(0, 9001, 0, "自定义颜色...");
        menu.add(0, 9002, 0, "重置颜色");
        menu.add(0, 9100, 0, "光标样式: 块状");
        menu.add(0, 9101, 0, "光标样式: 下划线");
        menu.add(0, 9102, 0, "光标样式: 竖线");
        menu.add(0, 9103, 0, "光标闪烁: 开");
        menu.add(0, 9104, 0, "光标颜色...");
        menu.add(0, 4001, 0, "行高 +");
        menu.add(0, 4002, 0, "行高 -");
        menu.add(0, 4003, 0, "字距 +");
        menu.add(0, 4004, 0, "字距 -");
        menu.add(0, 6001, 0, "背景图片...");
        menu.add(0, 6002, 0, "背景透明度 +");
        menu.add(0, 6003, 0, "背景透明度 -");
        menu.add(0, 6100, 0, "背景缩放: 填充");
        menu.add(0, 6101, 0, "背景缩放: 居中");
        menu.add(0, 6102, 0, "背景缩放: 裁剪");
        menu.add(0, 6103, 0, "背景缩放: 适应");
        menu.add(0, 3001, 0, "端口转发...");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        
        switch (id) {
            case 1001: applyFontSize(24); break;
            case 1002: applyFontSize(36); break;
            case 1003: applyFontSize(48); break;
            case 1004: applyFontSize(60); break;
            case 1005:
                currentFontFamily = 0;
                terminalPrefs.edit().putInt(PREF_FONT_FAMILY, currentFontFamily).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 1006:
                currentFontFamily = 1;
                terminalPrefs.edit().putInt(PREF_FONT_FAMILY, currentFontFamily).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 2001: applyColorScheme(SCHEME_DEFAULT); break;
            case 2002: applyColorScheme(SCHEME_SOLARIZED_DARK); break;
            case 2003: applyColorScheme(SCHEME_SOLARIZED_LIGHT); break;
            case 2004: applyColorScheme(SCHEME_MONOKAI); break;
            case 2005: applyColorScheme(THEME_DRACULA); break;
            case 2006: applyColorScheme(THEME_NORD); break;
            case 2007: applyColorScheme(THEME_GRUVBOX); break;
            case 2010: showColorSchemeEditor(); break;
            case 2011: showImportThemeDialog(); break;
            case 2012: showExportThemeDialog(); break;
            case 3002:
                keypadVisible = !keypadVisible;
                keypadView.setVisibility(keypadVisible ? View.VISIBLE : View.GONE);
                terminalPrefs.edit().putBoolean(PREF_KEYPAD_VISIBLE, keypadVisible).apply();
                break;
            case 5001:
                showCommandPanel();
                break;
            case 5002:
                showAddCommandDialog();
                break;
            case 7001:
                showSearchDialog();
                break;
            case 7002:
                clearSearch();
                break;
            case 8001:
                toggleSplitMode();
                break;
            case 8002:
                showSplitSelectDialog();
                break;
            case 9001:
                showCustomColorDialog();
                break;
            case 9002:
                resetCustomColors();
                break;
            case 9100:
                applyCursorStyle(com.orcterm.ui.widget.TerminalView.CursorStyle.BLOCK);
                break;
            case 9101:
                applyCursorStyle(com.orcterm.ui.widget.TerminalView.CursorStyle.UNDERLINE);
                break;
            case 9102:
                applyCursorStyle(com.orcterm.ui.widget.TerminalView.CursorStyle.BAR);
                break;
            case 9103:
                toggleCursorBlink();
                break;
            case 9104:
                showCursorColorDialog();
                break;
            case 4001:
                currentLineHeight = Math.min(3.0f, currentLineHeight + 0.1f);
                terminalPrefs.edit().putFloat(PREF_LINE_HEIGHT, currentLineHeight).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 4002:
                currentLineHeight = Math.max(0.6f, currentLineHeight - 0.1f);
                terminalPrefs.edit().putFloat(PREF_LINE_HEIGHT, currentLineHeight).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 4003:
                currentLetterSpacing = Math.min(0.3f, currentLetterSpacing + 0.02f);
                terminalPrefs.edit().putFloat(PREF_LETTER_SPACING, currentLetterSpacing).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 4004:
                currentLetterSpacing = Math.max(0.0f, currentLetterSpacing - 0.02f);
                terminalPrefs.edit().putFloat(PREF_LETTER_SPACING, currentLetterSpacing).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 6001:
                terminalBgPicker.launch("image/*");
                break;
            case 6002:
                terminalBackgroundAlpha = Math.min(255, terminalBackgroundAlpha + 10);
                terminalPrefs.edit().putInt(PREF_BG_ALPHA, terminalBackgroundAlpha).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 6003:
                terminalBackgroundAlpha = Math.max(0, terminalBackgroundAlpha - 10);
                terminalPrefs.edit().putInt(PREF_BG_ALPHA, terminalBackgroundAlpha).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 6100:
                applyBackgroundScale(com.orcterm.ui.widget.TerminalView.BackgroundScale.FILL);
                break;
            case 6101:
                applyBackgroundScale(com.orcterm.ui.widget.TerminalView.BackgroundScale.CENTER);
                break;
            case 6102:
                applyBackgroundScale(com.orcterm.ui.widget.TerminalView.BackgroundScale.CENTER_CROP);
                break;
            case 6103:
                applyBackgroundScale(com.orcterm.ui.widget.TerminalView.BackgroundScale.FIT_CENTER);
                break;
            case 3001: showPortForwardDialog(); break;
            default: return super.onOptionsItemSelected(item);
        }
        return true;
    }
    
    private void showPortForwardDialog() {
        if (activeContainer == null || activeContainer.session == null) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        EditText editLocalPort = new EditText(this);
        editLocalPort.setHint("本地端口 (如 8080)");
        editLocalPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(editLocalPort);
        
        EditText editRemoteHost = new EditText(this);
        editRemoteHost.setHint("远程主机 (如 localhost)");
        editRemoteHost.setText("localhost");
        layout.addView(editRemoteHost);
        
        EditText editRemotePort = new EditText(this);
        editRemotePort.setHint("远程端口 (如 80)");
        editRemotePort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(editRemotePort);
        
        new AlertDialog.Builder(this)
            .setTitle("添加端口转发 (SSH Tunnel)")
            .setView(layout)
            .setPositiveButton("启动", (dialog, which) -> {
                try {
                    int local = Integer.parseInt(editLocalPort.getText().toString().trim());
                    String host = editRemoteHost.getText().toString().trim();
                    int remote = Integer.parseInt(editRemotePort.getText().toString().trim());
                    
                    if (host.isEmpty()) {
                        Toast.makeText(this, "主机名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    activeContainer.session.startLocalForwarding(local, host, remote);
                    Toast.makeText(this, "转发已启动: " + local + " -> " + host + ":" + remote, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void applyFontSize(int size) {
        currentFontSize = size;
        for (TerminalContainer container : containers) {
            if (container.view != null) {
                container.view.setFontSize(size);
            }
        }
        terminalPrefs.edit().putInt("font_size_index", size == 24 ? 0 : size == 36 ? 1 : size == 48 ? 2 : 3).apply();
    }

    private int[] getEffectiveScheme() {
        int[] scheme = new int[16];
        System.arraycopy(currentScheme, 0, scheme, 0, 16);
        if (customBackgroundColor != null) {
            scheme[0] = customBackgroundColor;
        }
        if (customForegroundColor != null) {
            scheme[7] = customForegroundColor;
            scheme[15] = customForegroundColor;
        }
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

    private void applyColorScheme(int[] scheme) {
        currentScheme = scheme;
        for (TerminalContainer container : containers) {
            if (container.view != null) {
                container.view.setColorScheme(getEffectiveScheme());
            }
        }
        int idx = 0;
        if (scheme == SCHEME_SOLARIZED_DARK) idx = 1;
        else if (scheme == SCHEME_SOLARIZED_LIGHT) idx = 2;
        else if (scheme == SCHEME_MONOKAI) idx = 3;
        terminalPrefs.edit().putInt("terminal_theme_index", idx).apply();
    }

    // --- 通知辅助方法 ---

    private void updatePersistentNotification() {
        int connectedCount = 0;
        StringBuilder sb = new StringBuilder();
        for (TerminalContainer c : containers) {
            if (c.connected) {
                if (connectedCount > 0) sb.append(", ");
                sb.append(c.name);
                connectedCount++;
            }
        }
        SharedPreferences prefs = getSharedPreferences("orcterm_prefs", MODE_PRIVATE);
        prefs.edit()
            .putBoolean("persistent_notification_active", connectedCount > 0)
            .putString("persistent_notification_info", sb.toString())
            .apply();
        PersistentNotificationHelper.refresh(this);
    }

    private static class TerminalContainer {
        long id;
        String name;
        String group;
        boolean pinned;
        boolean connected;
        TerminalSession session;
        TerminalView view;
    }

    private class ContainerSessionListener implements TerminalSession.SessionListener {
        private final TerminalContainer container;

        private ContainerSessionListener(TerminalContainer container) {
            this.container = container;
        }

        @Override
        public void onConnected() {
            runOnUiThread(() -> {
                container.connected = true;
                container.view.append("Connected.\r\n");
                containerAdapter.notifyDataSetChanged();
                updatePersistentNotification();
                if (container == activeContainer) {
                    maybeRunInitialCommand(container);
                }
            });
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                container.connected = false;
                container.view.append("Disconnected.\r\n");
                containerAdapter.notifyDataSetChanged();
                updatePersistentNotification();
            });
        }

        @Override
        public void onDataReceived(String data) {
            runOnUiThread(() -> container.view.append(data));
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                container.connected = false;
                container.view.append("Error: " + message + "\r\n");
                containerAdapter.notifyDataSetChanged();
            });
        }
    }

    private class ContainerAdapter extends RecyclerView.Adapter<ContainerAdapter.ContainerViewHolder> {

        @NonNull
        @Override
        public ContainerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int padding = (int) (6 * parent.getResources().getDisplayMetrics().density);
            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setGravity(Gravity.CENTER_VERTICAL);
            layout.setPadding(padding * 2, padding, padding * 2, padding);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, padding, 0);
            layout.setLayoutParams(lp);

            TextView titleView = new TextView(parent.getContext());
            titleView.setTextColor(0xFFFFFFFF);
            titleView.setTextSize(12);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            layout.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView closeView = new TextView(parent.getContext());
            closeView.setText("×");
            closeView.setTextColor(0xFFB0B0B0);
            closeView.setTextSize(12);
            closeView.setPadding(padding, 0, 0, 0);
            layout.addView(closeView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            return new ContainerViewHolder(layout, titleView, closeView);
        }

        @Override
        public void onBindViewHolder(@NonNull ContainerViewHolder holder, int position) {
            TerminalContainer container = visibleContainers.get(position);
            String title = TextUtils.isEmpty(hostname) ? container.name : hostname;
            holder.titleView.setText(title);
            GradientDrawable bg = new GradientDrawable();
            int radius = (int) (12 * holder.itemView.getResources().getDisplayMetrics().density);
            bg.setCornerRadius(radius);
            bg.setColor(container == activeContainer ? 0xFF3A3A3A : 0xFF2A2A2A);
            holder.itemView.setBackground(bg);
            holder.itemView.setOnClickListener(v -> setActiveContainer(container));
            holder.itemView.setOnLongClickListener(v -> {
                showContainerMenu(v, container);
                return true;
            });
            holder.closeView.setOnClickListener(v -> closeContainer(container));
        }

        @Override
        public int getItemCount() {
            return visibleContainers.size();
        }

        class ContainerViewHolder extends RecyclerView.ViewHolder {
            TextView titleView;
            TextView closeView;

            ContainerViewHolder(@NonNull View itemView, TextView titleView, TextView closeView) {
                super(itemView);
                this.titleView = titleView;
                this.closeView = closeView;
            }
        }
    }

    private void showContainerMenu(View anchor, TerminalContainer container) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 0, 0, "重命名");
        menu.getMenu().add(0, 1, 1, "关闭");
        menu.getMenu().add(0, 2, 2, "复制");
        menu.getMenu().add(0, 3, 3, container.pinned ? "取消固定" : "固定");
        menu.getMenu().add(0, 4, 4, "分屏");
        menu.getMenu().add(0, 5, 5, "合并");
        if (splitMode) {
            menu.getMenu().add(0, 6, 6, splitVertical ? "切换为左右分屏" : "切换为上下分屏");
        }
        menu.getMenu().add(0, 7, 7, container.connected ? "断开连接" : "重连");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                showRenameContainerDialog(container);
                return true;
            }
            if (item.getItemId() == 1) {
                closeContainer(container);
                return true;
            }
            if (item.getItemId() == 2) {
                duplicateContainer(container);
                return true;
            }
            if (item.getItemId() == 3) {
                container.pinned = !container.pinned;
                refreshVisibleContainers();
                persistState();
                return true;
            }
            if (item.getItemId() == 4) {
                enableSplitWith(container);
                return true;
            }
            if (item.getItemId() == 5) {
                showMergeDialog(container);
                return true;
            }
            if (item.getItemId() == 6) {
                splitVertical = !splitVertical;
                applySplitLayout();
                return true;
            }
            if (item.getItemId() == 7) {
                if (container.connected) {
                    disconnectContainer(container);
                } else {
                    reconnectContainer(container);
                }
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void duplicateContainer(TerminalContainer source) {
        String name = source.name + " 副本";
        TerminalContainer container = createContainerInternal(name, source.group, nextContainerId++);
        refreshVisibleContainers();
        setActiveContainer(container);
    }

    private void enableSplitWith(TerminalContainer primary) {
        showSplitSelectDialog(primary);
    }

    private void showMergeDialog(TerminalContainer source) {
        if (!splitMode || activeContainer == null || splitSecondary == null) {
            Toast.makeText(this, "当前未开启分屏", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TerminalContainer> options = new ArrayList<>();
        if (source != null && (source == activeContainer || source == splitSecondary)) {
            options.add(source);
        }
        if (activeContainer != source) {
            options.add(activeContainer);
        }
        if (splitSecondary != source && splitSecondary != activeContainer) {
            options.add(splitSecondary);
        }
        if (options.isEmpty()) {
            Toast.makeText(this, "没有可合并的窗口", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            names[i] = options.get(i).name;
        }
        new AlertDialog.Builder(this)
            .setTitle("选择保留窗口")
            .setItems(names, (d, which) -> mergeSplitTo(options.get(which)))
            .show();
    }

    private void mergeSplitTo(TerminalContainer target) {
        if (target == null || !splitMode) {
            return;
        }
        activeContainer = target;
        disableSplitMode();
        setActiveContainer(target);
    }

    private void reconnectContainer(TerminalContainer container) {
        try {
            if (container.session != null) {
                container.session.disconnect();
            }
            container.connected = false;
            container.view.append("Reconnecting...\r\n");
            TerminalSession session = new TerminalSession();
            session.setListener(new ContainerSessionListener(container));
            session.connect(hostname, port, username, password, authType, keyPath);
            container.session = session;
            containerAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Toast.makeText(this, "重连失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectContainer(TerminalContainer container) {
        if (container.session != null) {
            container.session.disconnect();
            container.view.append("Disconnected by user.\r\n");
            container.session = null;
            container.connected = false;
            containerAdapter.notifyDataSetChanged();
        }
    }

    private void toggleSplitMode() {
        if (splitMode) {
            disableSplitMode();
        } else {
            showSplitSelectDialog();
        }
    }

    private void showSplitSelectDialog() {
        showSplitSelectDialog(activeContainer);
    }

    private void showSplitSelectDialog(TerminalContainer primary) {
        if (primary == null) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TerminalContainer> candidates = new ArrayList<>();
        for (TerminalContainer c : visibleContainers) {
            if (c != primary) {
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "需要至少两个标签", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            names[i] = candidates.get(i).name;
        }
        new AlertDialog.Builder(this)
            .setTitle("选择分屏窗口")
            .setItems(names, (d, which) -> enableSplit(primary, candidates.get(which)))
            .show();
    }

    private void enableSplit(TerminalContainer primary, TerminalContainer secondary) {
        if (primary == null || secondary == null) return;
        splitMode = true;
        activeContainer = primary;
        splitSecondary = secondary;
        terminalPrefs.edit()
            .putBoolean(PREF_SPLIT_MODE, true)
            .apply();
        applySplitLayout();
    }

    private void disableSplitMode() {
        splitMode = false;
        splitSecondary = null;
        terminalPrefs.edit()
            .putBoolean(PREF_SPLIT_MODE, false)
            .apply();
        if (containerHost == null) return;
        containerHost.removeAllViews();
        for (TerminalContainer container : containers) {
            if (container.view.getParent() instanceof ViewGroup) {
                ((ViewGroup) container.view.getParent()).removeView(container.view);
            }
            containerHost.addView(container.view);
            container.view.setVisibility(container == activeContainer ? View.VISIBLE : View.GONE);
        }
    }

    private void applySplitLayout() {
        if (!splitMode) {
            return;
        }
        if (activeContainer == null) {
            return;
        }
        if (splitSecondary == null || splitSecondary == activeContainer) {
            for (TerminalContainer c : visibleContainers) {
                if (c != activeContainer) {
                    splitSecondary = c;
                    break;
                }
            }
        }
        if (splitSecondary == null) {
            return;
        }
        if (splitLayout == null) {
            splitLayout = new android.widget.LinearLayout(this);
            splitPrimaryHost = new FrameLayout(this);
            splitSecondaryHost = new FrameLayout(this);
            splitDivider = new View(this);
            splitDivider.setBackgroundColor(0xFF444444);
            splitDivider.setOnTouchListener((v, e) -> {
                int action = e.getAction();
                if (action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_MOVE) {
                    splitRatio = calculateSplitRatio(e);
                    updateSplitWeights();
                    return true;
                }
                return action == android.view.MotionEvent.ACTION_UP;
            });
        }
        containerHost.removeAllViews();
        containerHost.addView(splitLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (splitVertical) {
            splitLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams lp1 = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, splitRatio);
            android.widget.LinearLayout.LayoutParams lp2 = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f - splitRatio);
            android.widget.LinearLayout.LayoutParams lpd = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
            splitLayout.removeAllViews();
            splitPrimaryHost.setLayoutParams(lp1);
            splitSecondaryHost.setLayoutParams(lp2);
            splitLayout.addView(splitPrimaryHost);
            splitLayout.addView(splitDivider, lpd);
            splitLayout.addView(splitSecondaryHost);
        } else {
            splitLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            android.widget.LinearLayout.LayoutParams lp1 = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, splitRatio);
            android.widget.LinearLayout.LayoutParams lp2 = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - splitRatio);
            android.widget.LinearLayout.LayoutParams lpd = new android.widget.LinearLayout.LayoutParams(dp(6), ViewGroup.LayoutParams.MATCH_PARENT);
            splitLayout.removeAllViews();
            splitPrimaryHost.setLayoutParams(lp1);
            splitSecondaryHost.setLayoutParams(lp2);
            splitLayout.addView(splitPrimaryHost);
            splitLayout.addView(splitDivider, lpd);
            splitLayout.addView(splitSecondaryHost);
        }
        terminalPrefs.edit()
            .putBoolean(PREF_SPLIT_VERTICAL, splitVertical)
            .putFloat(PREF_SPLIT_RATIO, splitRatio)
            .apply();
        attachViewToHost(activeContainer.view, splitPrimaryHost);
        attachViewToHost(splitSecondary.view, splitSecondaryHost);
        for (TerminalContainer container : containers) {
            if (container != activeContainer && container != splitSecondary) {
                container.view.setVisibility(View.GONE);
            }
        }
    }

    private void updateSplitWeights() {
        if (splitLayout == null || splitPrimaryHost == null || splitSecondaryHost == null) return;
        if (splitVertical) {
            android.widget.LinearLayout.LayoutParams lp1 = (android.widget.LinearLayout.LayoutParams) splitPrimaryHost.getLayoutParams();
            android.widget.LinearLayout.LayoutParams lp2 = (android.widget.LinearLayout.LayoutParams) splitSecondaryHost.getLayoutParams();
            lp1.weight = splitRatio;
            lp2.weight = 1f - splitRatio;
            splitPrimaryHost.setLayoutParams(lp1);
            splitSecondaryHost.setLayoutParams(lp2);
        } else {
            android.widget.LinearLayout.LayoutParams lp1 = (android.widget.LinearLayout.LayoutParams) splitPrimaryHost.getLayoutParams();
            android.widget.LinearLayout.LayoutParams lp2 = (android.widget.LinearLayout.LayoutParams) splitSecondaryHost.getLayoutParams();
            lp1.weight = splitRatio;
            lp2.weight = 1f - splitRatio;
            splitPrimaryHost.setLayoutParams(lp1);
            splitSecondaryHost.setLayoutParams(lp2);
        }
        splitLayout.requestLayout();
    }

    private float calculateSplitRatio(android.view.MotionEvent e) {
        int[] loc = new int[2];
        containerHost.getLocationOnScreen(loc);
        float ratio;
        if (splitVertical) {
            int height = containerHost.getHeight();
            if (height <= 0) return splitRatio;
            ratio = (e.getRawY() - loc[1]) / height;
        } else {
            int width = containerHost.getWidth();
            if (width <= 0) return splitRatio;
            ratio = (e.getRawX() - loc[0]) / width;
        }
        if (ratio < 0.2f) ratio = 0.2f;
        if (ratio > 0.8f) ratio = 0.8f;
        terminalPrefs.edit()
            .putFloat(PREF_SPLIT_RATIO, ratio)
            .apply();
        return ratio;
    }

    private void attachViewToHost(TerminalView view, FrameLayout host) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        host.removeAllViews();
        host.addView(view);
        view.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
    private void showSearchDialog() {
        if (activeContainer == null || activeContainer.view == null) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText input = new EditText(this);
        new AlertDialog.Builder(this)
            .setTitle("搜索高亮")
            .setView(input)
            .setPositiveButton("搜索", (d, w) -> {
                String q = input.getText().toString();
                activeContainer.view.setSearchQuery(q);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void clearSearch() {
        if (activeContainer == null || activeContainer.view == null) {
            return;
        }
        activeContainer.view.clearSearchQuery();
    }

    private void showCustomColorDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        EditText bg = new EditText(this);
        EditText fg = new EditText(this);
        bg.setHint("#000000");
        fg.setHint("#FFFFFF");
        bg.setText(customBackgroundColor != null ? formatColor(customBackgroundColor) : "");
        fg.setText(customForegroundColor != null ? formatColor(customForegroundColor) : "");
        layout.addView(bg);
        layout.addView(fg);
        new AlertDialog.Builder(this)
            .setTitle("自定义前景/背景颜色")
            .setView(layout)
            .setPositiveButton("应用", (d, w) -> {
                Integer bgColor = parseColorInput(bg.getText().toString());
                Integer fgColor = parseColorInput(fg.getText().toString());
                setCustomColors(bgColor, fgColor);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void resetCustomColors() {
        setCustomColors(null, null);
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
        applyDisplayPreferencesToAllViews();
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
            Toast.makeText(this, "颜色格式无效: " + input, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private String formatColor(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    private void initDefaultActions() {
        defaultActions.clear();
        defaultActions.add(new TerminalAction("清屏", "clear", false));
        defaultActions.add(new TerminalAction("列出目录", "ls -la", false));
        defaultActions.add(new TerminalAction("当前路径", "pwd", false));
        defaultActions.add(new TerminalAction("当前用户", "whoami", false));
    }

    private void loadCustomActions() {
        customActions.clear();
        String json = terminalPrefs.getString(PREF_CUSTOM_ACTIONS, null);
        if (json == null || json.isEmpty()) return;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String name = obj.optString("name");
                String cmd = obj.optString("command");
                if (!name.isEmpty() && !cmd.isEmpty()) {
                    customActions.add(new TerminalAction(name, cmd, true));
                }
            }
        } catch (JSONException ignored) {}
    }

    private void persistCustomActions() {
        JSONArray array = new JSONArray();
        for (TerminalAction action : customActions) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("name", action.name);
                obj.put("command", action.command);
                array.put(obj);
            } catch (JSONException ignored) {}
        }
        terminalPrefs.edit().putString(PREF_CUSTOM_ACTIONS, array.toString()).apply();
    }

    private void showCommandPanel() {
        TerminalSession session = getActiveSession();
        if (session == null) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TerminalAction> actions = new ArrayList<>();
        actions.addAll(defaultActions);
        actions.addAll(customActions);
        if (actions.isEmpty()) {
            Toast.makeText(this, "没有可用命令", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            names[i] = actions.get(i).name;
        }
        new AlertDialog.Builder(this)
            .setTitle("命令面板")
            .setItems(names, (d, which) -> handleActionSelection(actions.get(which)))
            .show();
    }

    private void handleActionSelection(TerminalAction action) {
        if (action.custom) {
            String[] options = {"执行", "编辑", "删除"};
            new AlertDialog.Builder(this)
                .setTitle(action.name)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        sendCommand(action.command);
                    } else if (which == 1) {
                        showEditCommandDialog(action);
                    } else if (which == 2) {
                        customActions.remove(action);
                        persistCustomActions();
                    }
                })
                .show();
        } else {
            sendCommand(action.command);
        }
    }

    private void showAddCommandDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        EditText name = new EditText(this);
        EditText cmd = new EditText(this);
        name.setHint("名称");
        cmd.setHint("命令");
        layout.addView(name);
        layout.addView(cmd);
        new AlertDialog.Builder(this)
            .setTitle("添加命令")
            .setView(layout)
            .setPositiveButton("保存", (d, w) -> {
                String n = name.getText().toString().trim();
                String c = cmd.getText().toString().trim();
                if (n.isEmpty() || c.isEmpty()) {
                    Toast.makeText(this, "名称和命令不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                customActions.add(new TerminalAction(n, c, true));
                persistCustomActions();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showEditCommandDialog(TerminalAction action) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        EditText name = new EditText(this);
        EditText cmd = new EditText(this);
        name.setText(action.name);
        cmd.setText(action.command);
        layout.addView(name);
        layout.addView(cmd);
        new AlertDialog.Builder(this)
            .setTitle("编辑命令")
            .setView(layout)
            .setPositiveButton("保存", (d, w) -> {
                String n = name.getText().toString().trim();
                String c = cmd.getText().toString().trim();
                if (n.isEmpty() || c.isEmpty()) {
                    Toast.makeText(this, "名称和命令不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                action.name = n;
                action.command = c;
                persistCustomActions();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void sendCommand(String command) {
        if (command == null) return;
        String eol = enterNewline ? "\n" : "\r";
        String cmd = command;
        if (!cmd.endsWith("\n") && !cmd.endsWith("\r")) {
            cmd += eol;
        }
        dispatchInput(cmd);
    }

    private void applyDisplayPreferencesToAllViews() {
        for (TerminalContainer container : containers) {
            TerminalView v = container.view;
            if (v == null) continue;
            v.setColorScheme(getEffectiveScheme());
            v.setLineHeightMultiplier(currentLineHeight);
            v.setLetterSpacing(currentLetterSpacing);
            Typeface tf = currentFontFamily == 0 ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
            v.setTypeface(tf);
            if (terminalBackground != null) {
                v.setBackgroundImage(terminalBackground);
                v.setBackgroundAlpha(terminalBackgroundAlpha);
            } else {
                v.setBackgroundImage(null);
            }
            applyCursorSettings(v);
            applyBackgroundSettings(v);
        }
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == to) return false;
                Collections.swap(visibleContainers, from, to);
                TerminalContainer a = visibleContainers.get(from);
                TerminalContainer b = visibleContainers.get(to);
                int ai = containers.indexOf(a);
                int bi = containers.indexOf(b);
                if (ai >= 0 && bi >= 0) {
                    Collections.swap(containers, ai, bi);
                }
                containerAdapter.notifyItemMoved(from, to);
                persistState();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        };
        new ItemTouchHelper(callback).attachToRecyclerView(containerList);
    }

    private void loadKeypadMappingFromPrefs() {
        keypadMapping.clear();
        String json = terminalPrefs.getString(PREF_KEYPAD_MAPPING, null);
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(json);
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String k = it.next();
                keypadMapping.put(k, obj.optString(k));
            }
        } catch (Exception ignored) {}
    }

    private void persistKeypadMapping() {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, String> e : keypadMapping.entrySet()) {
            try {
                obj.put(e.getKey(), e.getValue());
            } catch (JSONException ignored) {}
        }
        terminalPrefs.edit().putString(PREF_KEYPAD_MAPPING, obj.toString()).apply();
    }

    /**
     * 应用光标样式
     */
    private void applyCursorStyle(com.orcterm.ui.widget.TerminalView.CursorStyle style) {
        if (activeContainer != null && activeContainer.view != null) {
            activeContainer.view.setCursorStyle(style);
            terminalPrefs.edit().putInt("terminal_cursor_style", style.ordinal()).apply();
        }
    }

    /**
     * 切换光标闪烁
     */
    private void toggleCursorBlink() {
        boolean currentBlink = terminalPrefs.getBoolean("terminal_cursor_blink", true);
        boolean newBlink = !currentBlink;
        if (activeContainer != null && activeContainer.view != null) {
            activeContainer.view.setCursorBlink(newBlink);
            terminalPrefs.edit().putBoolean("terminal_cursor_blink", newBlink).apply();
        }
    }

    /**
     * 显示光标颜色选择对话框
     */
    private void showCursorColorDialog() {
        int currentColor = terminalPrefs.getInt("terminal_cursor_color", 0xFFFFFFFF);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        android.widget.EditText edit = new android.widget.EditText(this);
        edit.setHint("#FFFFFF (白色)");
        edit.setText(String.format("#%06X", (currentColor & 0x00FFFFFF)));
        layout.addView(edit);
        
        new AlertDialog.Builder(this)
            .setTitle("光标颜色")
            .setView(layout)
            .setPositiveButton("应用", (d, w) -> {
                String input = edit.getText().toString().trim();
                try {
                    Integer color = parseColorInput(input);
                    if (color != null) {
                        applyCursorColor(color);
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "无效的颜色值", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 应用光标颜色
     */
    private void applyCursorColor(int color) {
        if (activeContainer != null && activeContainer.view != null) {
            activeContainer.view.setCursorColor(color);
            terminalPrefs.edit().putInt("terminal_cursor_color", color).apply();
        }
    }



    /**
     * 应用所有光标设置到当前视图
     */
    private void applyCursorSettings(TerminalView view) {
        int styleIndex = terminalPrefs.getInt("terminal_cursor_style", 0);
        com.orcterm.ui.widget.TerminalView.CursorStyle[] styles = com.orcterm.ui.widget.TerminalView.CursorStyle.values();
        if (styleIndex >= 0 && styleIndex < styles.length) {
            view.setCursorStyle(styles[styleIndex]);
        }
        
        boolean blink = terminalPrefs.getBoolean("terminal_cursor_blink", true);
        view.setCursorBlink(blink);
        
        int color = terminalPrefs.getInt("terminal_cursor_color", 0xFFFFFFFF);
        view.setCursorColor(color);
    }

    /**
     * 应用背景缩放模式
     */
    private void applyBackgroundScale(com.orcterm.ui.widget.TerminalView.BackgroundScale scale) {
        if (activeContainer != null && activeContainer.view != null) {
            activeContainer.view.setBackgroundScale(scale);
            terminalPrefs.edit().putInt("terminal_background_scale", scale.ordinal()).apply();
        }
    }

    /**
     * 应用背景设置
     */
    private void applyBackgroundSettings(TerminalView view) {
        int scaleIndex = terminalPrefs.getInt("terminal_background_scale",3);
        com.orcterm.ui.widget.TerminalView.BackgroundScale[] scales = com.orcterm.ui.widget.TerminalView.BackgroundScale.values();
        if (scaleIndex >= 0 && scaleIndex < scales.length) {
            view.setBackgroundScale(scales[scaleIndex]);
        }
    }

    /**
     * 显示主题编辑器
     */
    private void showColorSchemeEditor() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("主题编辑器 - 16色调色板");
        
        // 创建垂直布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        // 为每个颜色创建选择器
        String[] colorNames = {"黑色", "红色", "绿色", "黄色", "蓝色", "洋红", "青色", "白色",
                            "亮黑", "亮红", "亮绿", "亮黄", "亮蓝", "亮洋红", "亮青", "亮白"};
        
        android.widget.LinearLayout colorLayout = new android.widget.LinearLayout(this);
        colorLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        
        for (int i = 0; i < 16; i++) {
            final int colorIndex = i;
            
            // 每行一个颜色选择器
            android.widget.LinearLayout rowLayout = new android.widget.LinearLayout(this);
            rowLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            
            // 标签
            android.widget.TextView label = new android.widget.TextView(this);
            label.setText(colorNames[i] + " (" + i + "):");
            label.setWidth(120);
            label.setPadding(0, 8, 16, 8);
            rowLayout.addView(label);
            
            // 颜色预览按钮
            android.widget.Button colorButton = new android.widget.Button(this);
            colorButton.setText("●");
            colorButton.setBackgroundColor(currentScheme[colorIndex]);
            colorButton.setWidth(50);
            colorButton.setHeight(40);
            colorButton.setPadding(0, 0, 0, 0);
            colorButton.setOnClickListener(v -> showColorPicker(colorIndex, colorButton));
            rowLayout.addView(colorButton);
            
            // 当前颜色值显示
            android.widget.TextView colorText = new android.widget.TextView(this);
            colorText.setText(String.format("#%06X", currentScheme[colorIndex] & 0x00FFFFFF));
            colorText.setPadding(16, 8, 0, 8);
            rowLayout.addView(colorText);
            
            colorLayout.addView(rowLayout);
        }
        
        // 添加滚动视图
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(colorLayout);
        layout.addView(scrollView, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 400));
        
        // 按钮区域
        android.widget.LinearLayout buttonLayout = new android.widget.LinearLayout(this);
        buttonLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(0, 16, 0, 0);
        
        android.widget.Button resetButton = new android.widget.Button(this);
        resetButton.setText("重置为默认");
        resetButton.setOnClickListener(v -> {
            applyColorScheme(SCHEME_DEFAULT);
            builder.create().dismiss();
        });
        
        android.widget.Button saveButton = new android.widget.Button(this);
        saveButton.setText("保存并应用");
        saveButton.setOnClickListener(v -> {
            saveCurrentColorScheme();
            applyDisplayPreferencesToAllViews();
            builder.create().dismiss();
        });
        
        buttonLayout.addView(resetButton);
        buttonLayout.addView(saveButton);
        layout.addView(buttonLayout);
        
        builder.setView(layout)
               .setNegativeButton("取消", null)
               .show();
    }

    /**
     * 显示颜色选择器
     */
    private void showColorPicker(final int colorIndex, final android.widget.Button colorButton) {
        android.widget.EditText edit = new android.widget.EditText(this);
        edit.setText(String.format("#%06X", currentScheme[colorIndex] & 0x00FFFFFF));
        edit.setHint("#FFFFFF");
        
        new AlertDialog.Builder(this)
            .setTitle("设置颜色 " + colorIndex)
            .setView(edit)
            .setPositiveButton("确定", (d, w) -> {
                String input = edit.getText().toString().trim();
                Integer color = parseColorInput(input);
                if (color != null) {
                    currentScheme[colorIndex] = color;
                    colorButton.setBackgroundColor(color);
                } else {
                    Toast.makeText(this, "无效的颜色值", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 保存当前配色方案
     */
    private void saveCurrentColorScheme() {
        SharedPreferences.Editor editor = terminalPrefs.edit();
        for (int i = 0; i < 16; i++) {
            editor.putInt("custom_color_" + i, currentScheme[i]);
        }
        editor.putInt("custom_theme_version", 1);
        editor.apply();
    }

    /**
     * 加载自定义配色方案
     */
    private void loadCustomColorScheme() {
        if (terminalPrefs.getInt("custom_theme_version", 0) > 0) {
            boolean hasCustomColors = false;
            for (int i = 0; i < 16; i++) {
                if (terminalPrefs.contains("custom_color_" + i)) {
                    currentScheme[i] = terminalPrefs.getInt("custom_color_" + i, currentScheme[i]);
                    hasCustomColors = true;
                }
            }
            if (hasCustomColors) {
                applyDisplayPreferencesToAllViews();
            }
        }
    }

    /**
     * 显示导入主题对话框
     */
    private void showImportThemeDialog() {
        android.widget.EditText edit = new android.widget.EditText(this);
        edit.setHint("粘贴主题JSON或以逗号分隔的16个颜色值");
        edit.setHeight(120);
        
        new AlertDialog.Builder(this)
            .setTitle("导入主题")
            .setView(edit)
            .setPositiveButton("导入", (d, w) -> {
                String input = edit.getText().toString().trim();
                if (input.startsWith("[")) {
                    importThemeFromJson(input);
                } else {
                    importThemeFromCsv(input);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 从JSON导入主题
     */
    private void importThemeFromJson(String json) {
        try {
            org.json.JSONArray array = new org.json.JSONArray(json);
            if (array.length() >= 16) {
                int[] colors = new int[16];
                for (int i = 0; i < 16; i++) {
                    colors[i] = parseColorFromValue(array.getString(i));
                }
                applyColorScheme(colors);
            } else {
                Toast.makeText(this, "主题需要包含至少16个颜色", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "无效的JSON格式", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 从CSV导入主题
     */
    private void importThemeFromCsv(String csv) {
        String[] parts = csv.split(",");
        if (parts.length >= 16) {
            int[] colors = new int[16];
            for (int i = 0; i < 16; i++) {
                colors[i] = parseColorFromValue(parts[i].trim());
            }
            applyColorScheme(colors);
        } else {
            Toast.makeText(this, "需要至少16个颜色值", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 从值解析颜色
     */
    private int parseColorFromValue(String value) {
        value = value.trim();
        if (value.startsWith("#")) {
            try {
                return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
            } catch (NumberFormatException e) {
                return currentScheme[0]; // 默认黑色
            }
        }
        return currentScheme[0]; // 默认黑色
    }

    /**
     * 显示导出主题对话框
     */
    private void showExportThemeDialog() {
        // 创建JSON格式
        org.json.JSONArray jsonArray = new org.json.JSONArray();
        for (int i = 0; i < 16; i++) {
            jsonArray.put(String.format("#%06X", currentScheme[i] & 0x00FFFFFF));
        }
        
        String jsonExport = jsonArray.toString();
        
        // 创建CSV格式
        StringBuilder csvBuilder = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0) csvBuilder.append(", ");
            csvBuilder.append(String.format("#%06X", currentScheme[i] & 0x00FFFFFF));
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
        jsonRadio.setText("JSON格式");
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
        preview.setTypeface(android.graphics.Typeface.MONOSPACE);
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

    private static class TerminalAction {
        String name;
        String command;
        boolean custom;

        TerminalAction(String name, String command, boolean custom) {
            this.name = name;
            this.command = command;
            this.custom = custom;
        }
    }
}
