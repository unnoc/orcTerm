package com.orcterm.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.button.MaterialButton;
import com.orcterm.R;
import com.orcterm.core.terminal.TerminalSession;
import com.orcterm.core.transport.HostKeyVerifier;
import com.orcterm.util.CommandConstants;
import com.orcterm.util.PersistentNotificationHelper;
import com.orcterm.util.SessionLogManager;
import com.orcterm.util.CommandHistoryManager;
import com.orcterm.ui.adapter.AutocompleteAdapter;
import com.orcterm.ui.widget.TerminalInputView;
import com.orcterm.ui.widget.TerminalKeypadView;
import com.orcterm.ui.widget.TerminalView;
import com.orcterm.core.session.SessionInfo;
import com.orcterm.core.session.SessionManager;
import com.orcterm.core.terminal.TerminalEmulator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    private static final String PREF_THEME_JSON = "terminal_theme_json";
    private static final String PREF_FONT_SIZE_PX = "terminal_font_size_px";
    private static final String PREF_THEME_ID = "terminal_theme_id";
    private static final String PREF_THEME_CREATED_AT = "terminal_theme_created_at";
    private static final String PREF_FONT_WEIGHT = "terminal_font_weight";
    private static final String PREF_SELECTION_COLOR = "terminal_selection_color";
    private static final String PREF_SEARCH_HIGHLIGHT_COLOR = "terminal_search_highlight_color";
    private static final String PREF_LAST_HOSTNAME = "terminal_last_hostname";
    private static final String PREF_LAST_USERNAME = "terminal_last_username";
    private static final String PREF_LAST_PORT = "terminal_last_port";
    private static final String PREF_LAST_PASSWORD = "terminal_last_password";
    private static final String PREF_LAST_AUTH_TYPE = "terminal_last_auth_type";
    private static final String PREF_LAST_KEY_PATH = "terminal_last_key_path";
    private static final int MAX_HISTORY = 50;

    private static class ConnectionParams {
        String host;
        int port;
        String user;
        String password;
        int authType;
        String keyPath;
    }

    private boolean hostScoped;
    private String scopedHost;
    private String scopedUser;
    private int scopedPort;

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

    private static final int[] THEME_TERMIUS = {
        0xFF151A1E, 0xFFF75F5F, 0xFF7FD962, 0xFFF2C94C,
        0xFF5AA9FF, 0xFFC792EA, 0xFF5ED4F4, 0xFFE6EDF3,
        0xFF5A6B7A, 0xFFFF7070, 0xFF9BE77C, 0xFFF6D06F,
        0xFF7BB9FF, 0xFFD6A7F0, 0xFF7FE3F8, 0xFFFFFFFF
    };
    private static final int[] THEME_HIGH_CONTRAST = {
        0xFF000000, 0xFFFF3B30, 0xFF34C759, 0xFFFFCC00,
        0xFF0A84FF, 0xFFFF2D55, 0xFF64D2FF, 0xFFFFFFFF,
        0xFF3A3A3C, 0xFFFF453A, 0xFF30D158, 0xFFFFD60A,
        0xFF5E5CE6, 0xFFFF375F, 0xFF70D7FF, 0xFFFFFFFF
    };
    
    private int currentFontSize = 36;
    private int[] currentScheme = THEME_TERMIUS;
    private float currentLineHeight = 1.0f;
    private float currentLetterSpacing = 0.0f;
    private int currentFontFamily = 0;
    private int currentFontWeight = 400;
    private Bitmap terminalBackground;
    private int terminalBackgroundAlpha = 255;
    private boolean keypadVisible = true;
    private Integer customBackgroundColor;
    private Integer customForegroundColor;
    private int currentSelectionColor = 0x5533B5E5;
    private int currentSearchHighlightColor = 0x66FFD54F;
    private boolean enterNewline = true;
    private boolean localEcho = false;
    private boolean immersiveMode = false;

    // UI 组件
    private FrameLayout containerHost;
    private RecyclerView containerList;
    private MaterialButton btnAddContainer;
    private MaterialButton btnToggleKeypad;
    private TerminalInputView inputCommand;
    private TerminalKeypadView keypadView;
    private LinearLayout keyboardControlBar;
    private TextView tvKeyboardStatus;
    private MaterialButton btnShowKeyboardOptions;
    private TextView terminalTitle;
    private LinearLayout terminalEmptyState;
    private MaterialButton terminalEmptyAction;

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
    
    // 会话日志和命令历史管理器
    private SessionLogManager sessionLogManager;
    private CommandHistoryManager commandHistoryManager;
    private boolean sessionLoggingEnabled = true;
    private boolean commandCompletionEnabled = true;
    
    // 命令自动补全 UI
    private RecyclerView autocompleteRecyclerView;
    private AutocompleteAdapter autocompleteAdapter;
    private android.widget.LinearLayout autocompleteContainer;
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
        
        // Only clear sessions if this is a fresh start, not a recreation
        // if (savedInstanceState == null) {
        //    SessionManager.getInstance().clearSessions();
        // }
        
        setContentView(R.layout.activity_terminal);

        // 适配系统窗口插图 (Safe Area)，确保底部虚拟键盘不被系统导航栏遮挡
        View root = findViewById(android.R.id.content);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            findViewById(R.id.input_container).setPadding(0, 0, 0, bottom);
            return insets;
        });

        // 初始化视图
        containerHost = findViewById(R.id.container_host);
        containerList = findViewById(R.id.container_list);
        btnAddContainer = findViewById(R.id.btn_add_container);
        inputCommand = findViewById(R.id.input_command);
        keypadView = findViewById(R.id.keypad_view);
        keyboardControlBar = findViewById(R.id.keyboard_control_bar);
        tvKeyboardStatus = findViewById(R.id.tv_keyboard_status);
        btnToggleKeypad = findViewById(R.id.btn_toggle_keypad);
        btnShowKeyboardOptions = findViewById(R.id.btn_keyboard_options);
        terminalTitle = findViewById(R.id.terminal_title);
        terminalEmptyState = findViewById(R.id.terminal_empty_state);
        terminalEmptyAction = findViewById(R.id.terminal_empty_action);
        terminalPrefs = getSharedPreferences(PREF_APP, MODE_PRIVATE);
        int themeIndex = terminalPrefs.getInt("terminal_theme_index", 0);
        if (themeIndex == 1) currentScheme = SCHEME_SOLARIZED_DARK;
        else if (themeIndex == 2) currentScheme = SCHEME_SOLARIZED_LIGHT;
        else if (themeIndex == 3) currentScheme = SCHEME_MONOKAI;
        else if (themeIndex == 4) currentScheme = THEME_TERMIUS;
        int fontIndex = terminalPrefs.getInt("font_size_index", 1);
        if (terminalPrefs.contains(PREF_FONT_SIZE_PX)) {
            currentFontSize = terminalPrefs.getInt(PREF_FONT_SIZE_PX, 36);
        } else {
            if (fontIndex == 0) currentFontSize = 24;
            else if (fontIndex == 1) currentFontSize = 36;
            else if (fontIndex == 2) currentFontSize = 48;
            else if (fontIndex == 3) currentFontSize = 60;
        }
        currentLineHeight = terminalPrefs.getFloat(PREF_LINE_HEIGHT, 1.0f);
        currentLetterSpacing = terminalPrefs.getFloat(PREF_LETTER_SPACING, 0.0f);
        currentFontFamily = terminalPrefs.getInt(PREF_FONT_FAMILY, 0);
        currentFontWeight = terminalPrefs.getInt(PREF_FONT_WEIGHT, 400);
        terminalBackgroundAlpha = terminalPrefs.getInt(PREF_BG_ALPHA, 255);
        enterNewline = terminalPrefs.getBoolean(PREF_ENTER_NEWLINE, true);
        localEcho = false; // terminalPrefs.getBoolean(PREF_LOCAL_ECHO, false);
        currentSelectionColor = terminalPrefs.getInt(PREF_SELECTION_COLOR, 0x5533B5E5);
        currentSearchHighlightColor = terminalPrefs.getInt(PREF_SEARCH_HIGHLIGHT_COLOR, 0x66FFD54F);
        if (terminalPrefs.contains(PREF_CUSTOM_BG)) {
            customBackgroundColor = terminalPrefs.getInt(PREF_CUSTOM_BG, 0xFF000000);
        }
        if (terminalPrefs.contains(PREF_CUSTOM_FG)) {
            customForegroundColor = terminalPrefs.getInt(PREF_CUSTOM_FG, 0xFFFFFFFF);
        }
        
        // 初始化会话日志和命令历史管理器
        sessionLogManager = new SessionLogManager(this);
        commandHistoryManager = new CommandHistoryManager(this);
        sessionLoggingEnabled = terminalPrefs.getBoolean("terminal_session_logging_enabled", true);
        commandCompletionEnabled = terminalPrefs.getBoolean("terminal_command_completion_enabled", true);
        
        // 初始化命令自动补全 UI
        setupAutocompleteView();
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
        if (keyboardControlBar != null) {
            keyboardControlBar.setVisibility(View.VISIBLE);
        }
        if (tvKeyboardStatus != null) {
            tvKeyboardStatus.setText(keypadVisible ? getString(R.string.terminal_keyboard_status_shown)
                    : getString(R.string.terminal_keyboard_status_hidden));
        }
        if (btnToggleKeypad != null) {
            btnToggleKeypad.setText(keypadVisible ? getString(R.string.terminal_keyboard_toggle_hide)
                    : getString(R.string.terminal_keyboard_toggle_show));
            btnToggleKeypad.setOnClickListener(v -> toggleKeypadVisibility());
        }
        int layoutMode = terminalPrefs.getInt("keyboard_layout_option", 0);
        keypadView.setLayoutMode(layoutMode);
        keypadView.setLayoutChangeListener(mode -> {
            terminalPrefs.edit().putInt("keyboard_layout_option", mode).apply();
            String[] layouts = {"标准", "编程", "服务器管理"};
            Toast.makeText(this, "已切换键盘布局: " + layouts[mode], Toast.LENGTH_SHORT).show();
        });
        if (terminalEmptyAction != null) {
            terminalEmptyAction.setOnClickListener(v -> showCreateContainerDialog());
        }
        updateTerminalTitle();
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

        hostname = getIntent().getStringExtra("hostname");
        username = getIntent().getStringExtra("username");
        port = getIntent().getIntExtra("port", 22);
        password = getIntent().getStringExtra("password");
        authType = getIntent().getIntExtra("auth_type", 0);
        keyPath = getIntent().getStringExtra("key_path");
        if (!TextUtils.isEmpty(hostname)) {
            hostScoped = true;
            scopedHost = hostname;
            scopedUser = username;
            scopedPort = port;
        }
        initialCommand = getIntent().getStringExtra("initial_command");
        if (initialCommand == null) {
            initialCommand = getIntent().getStringExtra("initialCommand");
        }
        restoreConnectionParamsIfMissing();
        persistConnectionParams();
        applyThemeJsonFromPrefs();
        handleThemeAction(getIntent());
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
        
        // 根据偏好设置初始化键盘高度
        applyKeyboardHeightSetting();

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
                    } else if (keyCode == KeyEvent.KEYCODE_TAB) {
                        // Tab键用于在终端和键盘间切换焦点
                        if (event.isShiftPressed()) {
                            // Shift+Tab: 返回终端视图
                            if (activeContainer != null && activeContainer.view != null) {
                                activeContainer.view.requestFocus();
                            }
                        } else {
                            // Tab: 触发自动补全或发送Tab字符
                            if (autocompleteContainer != null && autocompleteContainer.getVisibility() == View.VISIBLE && 
                                autocompleteAdapter != null && autocompleteAdapter.getItemCount() > 0) {
                                // 如果有补全建议，选择第一个
                                CommandHistoryManager.CommandSuggestion suggestion = autocompleteAdapter.getItem(0);
                                if (suggestion != null) {
                                    onAutocompleteSelected(suggestion.command);
                                }
                            } else {
                                // 否则发送Tab字符
                                dispatchInput("\t");
                            }
                        }
                        return; // 消费此事件
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
            if ("KBD".equals(value)) {
                toggleKeypadVisibility();
                return;
            }
            if (activeContainer == null) {
                return;
            }
            dispatchInput(value);
        });
        keypadView.setLongClickListener((label, value) -> {
            if (activeContainer == null) {
                return;
            }
            if ("↑".equals(label)) {
                showCommandHistoryTimeline();
                return;
            }
            if ("←".equals(label)) {
                dispatchInput("\033[1;5D");
                return;
            }
            if ("→".equals(label)) {
                dispatchInput("\033[1;5C");
                return;
            }
            if ("↓".equals(label)) {
                dispatchInput("\033[1;5B");
                return;
            }
            // 长按功能键时可显示快捷功能
            if (label.startsWith("F") && label.length() > 1 && Character.isDigit(label.charAt(1))) {
                Toast.makeText(this, "长按功能键: " + label, Toast.LENGTH_SHORT).show();
                return;
            }
            dispatchInput(value);
        });
        
        // 设置键盘选项按钮的点击事件
        if (btnShowKeyboardOptions != null) {
            btnShowKeyboardOptions.setOnClickListener(v -> {
                showKeyboardOptionsDialog();
            });
        }

        loadState();
        restoreSessions();
        syncSessionsFromContainers();
        refreshVisibleContainers();
        
        // Handle focus intent if present
        long focusId = getIntent().getLongExtra("focus_container_id", -1);
        if (focusId != -1) {
            for (TerminalContainer c : containers) {
                if (c.id == focusId) {
                    setActiveContainer(c);
                    break;
                }
            }
        } else if (!TextUtils.isEmpty(hostname)) {
             // 检查是否已存在该主机的会话
             boolean found = false;
             for (TerminalContainer c : containers) {
                 if (c.session != null && 
                     hostname.equals(c.session.getHost()) && 
                     port == c.session.getPort() &&
                     (username == null || username.equals(c.session.getUsername()))) {
                     setActiveContainer(c);
                     found = true;
                     break;
                 }
             }
             
             if (!found) {
                 SessionInfo info = findSessionInfoForHost(hostname, port, username);
                 if (info != null) {
                     TerminalContainer restored = ensureContainerForSession(info);
                     if (restored != null) {
                         setActiveContainer(restored);
                         refreshVisibleContainers();
                         return;
                     }
                 }
                 // 如果不存在，创建新容器
                 long newId = nextContainerId++;
                 TerminalContainer newContainer = createContainerInternal(hostname, currentGroup, newId);
                 setActiveContainer(newContainer);
                 refreshVisibleContainers();
             }
        }
        
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
            } else if (PREF_FONT_WEIGHT.equals(key) || PREF_SELECTION_COLOR.equals(key) || PREF_SEARCH_HIGHLIGHT_COLOR.equals(key)) {
                currentFontWeight = prefs.getInt(PREF_FONT_WEIGHT, currentFontWeight);
                currentSelectionColor = prefs.getInt(PREF_SELECTION_COLOR, currentSelectionColor);
                currentSearchHighlightColor = prefs.getInt(PREF_SEARCH_HIGHLIGHT_COLOR, currentSearchHighlightColor);
                applyDisplayPreferencesToAllViews();
            } else if (PREF_THEME_JSON.equals(key)) {
                applyThemeJsonFromPrefs();
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

    private void toggleKeypadVisibility() {
        keypadVisible = !keypadVisible;
        keypadView.setVisibility(keypadVisible ? View.VISIBLE : View.GONE);
        if (keyboardControlBar != null) {
            keyboardControlBar.setVisibility(View.VISIBLE);
        }
        terminalPrefs.edit().putBoolean(PREF_KEYPAD_VISIBLE, keypadVisible).apply();
        
        // 更新键盘状态文本
        if (tvKeyboardStatus != null) {
            tvKeyboardStatus.setText(keypadVisible ? getString(R.string.terminal_keyboard_status_shown)
                    : getString(R.string.terminal_keyboard_status_hidden));
        }
        if (btnToggleKeypad != null) {
            btnToggleKeypad.setText(keypadVisible ? getString(R.string.terminal_keyboard_toggle_hide)
                    : getString(R.string.terminal_keyboard_toggle_show));
        }
        
        if (keypadVisible) {
            inputCommand.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(inputCommand, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            // 如果隐藏键盘，则隐藏软键盘
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        }
    }

    private void refreshVisibleContainers() {
        visibleContainers.clear();
        List<TerminalContainer> pinned = new ArrayList<>();
        List<TerminalContainer> normal = new ArrayList<>();
        for (TerminalContainer container : containers) {
            if (!matchesScopedContainer(container)) {
                continue;
            }
            if (container.pinned) {
                pinned.add(container);
            } else {
                normal.add(container);
            }
        }
        visibleContainers.addAll(pinned);
        visibleContainers.addAll(normal);
        containerAdapter.notifyDataSetChanged();
        updateTerminalEmptyState();
        if (visibleContainers.isEmpty()) {
            return;
        }
        if (activeContainer == null || !activeContainer.group.equals(currentGroup)) {
            setActiveContainer(visibleContainers.get(0));
        }
    }

    private String defaultContainerName() {
        if (!TextUtils.isEmpty(hostname)) {
            int count = 0;
            for (TerminalContainer container : containers) {
                if (container.session != null && TextUtils.equals(hostname, container.session.getHost())) {
                    count++;
                }
            }
            if (count > 0) {
                return hostname + " (" + (count + 1) + ")";
            }
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

    private void restoreConnectionParamsIfMissing() {
        if (terminalPrefs == null) return;
        boolean hostMissing = TextUtils.isEmpty(hostname);
        boolean userMissing = TextUtils.isEmpty(username);
        if (!hostMissing && !userMissing) return;
        if (hostMissing) {
            String savedHost = terminalPrefs.getString(PREF_LAST_HOSTNAME, null);
            if (!TextUtils.isEmpty(savedHost)) hostname = savedHost;
        }
        if (userMissing) {
            String savedUser = terminalPrefs.getString(PREF_LAST_USERNAME, null);
            if (!TextUtils.isEmpty(savedUser)) username = savedUser;
        }
        if (terminalPrefs.contains(PREF_LAST_PORT)) {
            port = terminalPrefs.getInt(PREF_LAST_PORT, port);
        }
        if (terminalPrefs.contains(PREF_LAST_AUTH_TYPE)) {
            authType = terminalPrefs.getInt(PREF_LAST_AUTH_TYPE, authType);
        }
        if (terminalPrefs.contains(PREF_LAST_PASSWORD)) {
            password = terminalPrefs.getString(PREF_LAST_PASSWORD, password);
        }
        if (terminalPrefs.contains(PREF_LAST_KEY_PATH)) {
            keyPath = terminalPrefs.getString(PREF_LAST_KEY_PATH, keyPath);
        }
    }

    private void persistConnectionParams() {
        if (terminalPrefs == null) return;
        if (TextUtils.isEmpty(hostname) || TextUtils.isEmpty(username)) return;
        SharedPreferences.Editor editor = terminalPrefs.edit();
        editor.putString(PREF_LAST_HOSTNAME, hostname);
        editor.putString(PREF_LAST_USERNAME, username);
        editor.putInt(PREF_LAST_PORT, port);
        editor.putInt(PREF_LAST_AUTH_TYPE, authType);
        if (password != null) {
            editor.putString(PREF_LAST_PASSWORD, password);
        } else {
            editor.remove(PREF_LAST_PASSWORD);
        }
        if (keyPath != null) {
            editor.putString(PREF_LAST_KEY_PATH, keyPath);
        } else {
            editor.remove(PREF_LAST_KEY_PATH);
        }
        editor.apply();
    }

    // --- 对话框辅助方法 ---

    private void showCreateContainerDialog() {
        if (TextUtils.isEmpty(hostname) || TextUtils.isEmpty(username)) {
            Toast.makeText(this, "请先选择主机再创建会话", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = defaultContainerName();
        TerminalContainer container = createContainerInternal(name, currentGroup, nextContainerId++, buildParamsFromCurrent());
        refreshVisibleContainers();
        setActiveContainer(container);
    }

    private ConnectionParams buildParamsFromCurrent() {
        ConnectionParams params = new ConnectionParams();
        params.host = hostname;
        params.port = port;
        params.user = username;
        params.password = password;
        params.authType = authType;
        params.keyPath = keyPath;
        return params;
    }

    private ConnectionParams buildParamsFromInfo(SessionInfo info) {
        ConnectionParams params = new ConnectionParams();
        if (info == null) return params;
        params.host = info.hostname;
        params.port = info.port;
        params.user = info.username;
        params.password = info.password;
        params.authType = info.authType;
        params.keyPath = info.keyPath;
        return params;
    }

    private ConnectionParams buildParamsForContainer(TerminalContainer container) {
        SessionInfo info = findSessionInfoById(container != null ? container.id : -1);
        ConnectionParams params = buildParamsFromInfo(info);
        if (container != null && container.session != null) {
            params.host = container.session.getHost();
            params.port = container.session.getPort();
            params.user = container.session.getUsername();
            params.password = container.session.getPassword();
            params.authType = container.session.getAuthType();
            params.keyPath = container.session.getKeyPath();
        }
        return params;
    }

    private TerminalContainer findContainerForHost(String host, int port, String user) {
        for (TerminalContainer container : containers) {
            TerminalSession session = container.session;
            if (session == null) continue;
            if (TextUtils.equals(host, session.getHost())
                    && port == session.getPort()
                    && TextUtils.equals(user, session.getUsername())) {
                return container;
            }
        }
        return null;
    }

    private TerminalSession findSharedSessionForHost(String host, int port, String user) {
        TerminalContainer existing = findContainerForHost(host, port, user);
        if (existing != null && existing.session != null) {
            return existing.session;
        }
        for (SessionInfo info : SessionManager.getInstance().getSessions()) {
            if (info == null) continue;
            if (info.port != port) continue;
            if (!TextUtils.equals(host, info.hostname)) continue;
            if (!TextUtils.equals(user, info.username)) continue;
            TerminalSession session = SessionManager.getInstance().getTerminalSession(info.id);
            if (session != null) return session;
        }
        return null;
    }

    private SessionInfo findSessionInfoForHost(String host, int port, String user) {
        for (SessionInfo info : SessionManager.getInstance().getSessions()) {
            if (info == null) continue;
            if (info.port != port) continue;
            if (!TextUtils.equals(host, info.hostname)) continue;
            if (!TextUtils.equals(user, info.username)) continue;
            return info;
        }
        return null;
    }

    private SessionInfo findSessionInfoById(long id) {
        if (id <= 0) return null;
        for (SessionInfo info : SessionManager.getInstance().getSessions()) {
            if (info != null && info.id == id) {
                return info;
            }
        }
        return null;
    }

    private boolean matchesScopedInfo(SessionInfo info) {
        if (!hostScoped || info == null) return true;
        if (!TextUtils.equals(scopedHost, info.hostname)) return false;
        if (scopedPort > 0 && info.port != scopedPort) return false;
        return TextUtils.equals(scopedUser, info.username);
    }

    private boolean matchesScopedContainer(TerminalContainer container) {
        if (!hostScoped || container == null) return true;
        if (container.session != null) {
            if (!TextUtils.equals(scopedHost, container.session.getHost())) return false;
            if (scopedPort > 0 && container.session.getPort() != scopedPort) return false;
            return TextUtils.equals(scopedUser, container.session.getUsername());
        }
        return matchesScopedInfo(findSessionInfoById(container.id));
    }

    private boolean hasOtherContainersForSession(TerminalSession session, TerminalContainer exclude) {
        if (session == null) return false;
        for (TerminalContainer container : containers) {
            if (container == exclude) continue;
            if (container.session == session) {
                return true;
            }
        }
        return false;
    }

    private void removeSessionEntriesForSession(TerminalSession session) {
        if (session == null) return;
        List<SessionInfo> infos = SessionManager.getInstance().getSessions();
        for (SessionInfo info : infos) {
            TerminalSession mapped = SessionManager.getInstance().getTerminalSession(info.id);
            if (mapped == session) {
                SessionManager.getInstance().removeSession(info.id);
            }
        }
    }

    private void syncConnectionParamsFromContainer(TerminalContainer container) {
        if (container == null) return;
        if (container.session != null) {
            hostname = container.session.getHost();
            port = container.session.getPort();
            username = container.session.getUsername();
            password = container.session.getPassword();
            authType = container.session.getAuthType();
            keyPath = container.session.getKeyPath();
            persistConnectionParams();
            return;
        }
        SessionInfo info = findSessionInfoById(container.id);
        if (info != null) {
            hostname = info.hostname;
            port = info.port;
            username = info.username;
            password = info.password;
            authType = info.authType;
            keyPath = info.keyPath;
            persistConnectionParams();
        }
    }

    private TerminalContainer ensureContainerForSession(SessionInfo info) {
        if (info == null) return null;
        for (TerminalContainer container : containers) {
            if (container.id == info.id) {
                return container;
            }
        }
        return createContainerInternal(info.name, currentGroup, info.id, buildParamsFromInfo(info));
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
        return createContainerInternal(name, group, id, buildParamsFromCurrent());
    }

    private TerminalContainer createContainerInternal(String name, String group, long id, ConnectionParams params) {
        TerminalContainer container = new TerminalContainer();
        container.id = id;
        container.name = name;
        container.group = group;
        container.inputBuffer = new StringBuilder();
        container.commandHistory = new ArrayList<>();

        // 初始化会话
        ConnectionParams resolved = params != null ? params : buildParamsFromCurrent();
        TerminalSession session = SessionManager.getInstance().getTerminalSession(id);
        if (session == null && !TextUtils.isEmpty(resolved.host)) {
            session = findSharedSessionForHost(resolved.host, resolved.port, resolved.user);
        }
        boolean isNewSession = (session == null);

        if (isNewSession) {
            boolean hasParams = !TextUtils.isEmpty(resolved.host) && !TextUtils.isEmpty(resolved.user);
            if (hasParams) {
                SessionInfo existingInfo = findSessionInfoForHost(resolved.host, resolved.port, resolved.user);
                session = new TerminalSession();
                session.setHostKeyVerifier(createHostKeyVerifier());
                long sharedHandle = 0;
                if (existingInfo != null) {
                    sharedHandle = SessionManager.getInstance().getAndRemoveSharedHandle(existingInfo.id);
                }
                if (sharedHandle != 0) {
                    try {
                        session.attachExistingSshHandle(sharedHandle, resolved.host, resolved.port, resolved.user, resolved.password, resolved.authType, resolved.keyPath);
                    } catch (Exception e) {
                        session.connect(resolved.host, resolved.port, resolved.user, resolved.password, resolved.authType, resolved.keyPath);
                    }
                } else {
                    session.connect(resolved.host, resolved.port, resolved.user, resolved.password, resolved.authType, resolved.keyPath);
                }
                SessionManager.getInstance().upsertSession(
                    new SessionInfo(container.id, container.name, resolved.host, resolved.port, resolved.user, resolved.password, resolved.authType, resolved.keyPath, false),
                    session
                );
                if (existingInfo != null && existingInfo.id != container.id) {
                    SessionManager.getInstance().upsertSession(
                        new SessionInfo(existingInfo.id, existingInfo.name, resolved.host, resolved.port, resolved.user, resolved.password, resolved.authType, resolved.keyPath, false),
                        session
                    );
                }
            }
        } else {
            container.connected = session.isConnected();
            String infoHost = !TextUtils.isEmpty(resolved.host) ? resolved.host : session.getHost();
            int infoPort = !TextUtils.isEmpty(resolved.host) ? resolved.port : session.getPort();
            String infoUser = !TextUtils.isEmpty(resolved.host) ? resolved.user : session.getUsername();
            String infoPass = !TextUtils.isEmpty(resolved.host) ? resolved.password : session.getPassword();
            int infoAuth = !TextUtils.isEmpty(resolved.host) ? resolved.authType : session.getAuthType();
            String infoKey = !TextUtils.isEmpty(resolved.host) ? resolved.keyPath : session.getKeyPath();
            SessionInfo existingInfo = findSessionInfoForHost(infoHost, infoPort, infoUser);
            if (!TextUtils.isEmpty(infoHost) && (existingInfo == null || existingInfo.id == container.id)) {
                SessionManager.getInstance().upsertSession(
                    new SessionInfo(
                        container.id,
                        container.name,
                        infoHost,
                        infoPort,
                        infoUser,
                        infoPass,
                        infoAuth,
                        infoKey,
                        container.connected
                    ),
                    session
                );
            }
        }

        if (session != null) {
            ContainerSessionListener listener = new ContainerSessionListener(container);
            session.addListener(listener);
            container.sessionListener = listener;
        }
        container.session = session;

        // 初始化视图
        TerminalView view = new TerminalView(this);
        TerminalEmulator emulator = new TerminalEmulator(80, 24);
        container.emulator = emulator;
        view.attachEmulator(emulator);
        view.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        view.setFontSize(currentFontSize);
        view.setColorScheme(getEffectiveScheme());
        view.setOnResizeListener((cols, rows) -> {
            if (container.session != null) {
                container.session.resize(cols, rows);
            }
        });
        view.setLineHeightMultiplier(currentLineHeight);
        view.setLetterSpacing(currentLetterSpacing);
        Typeface tf = currentFontFamily == 0 ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
        view.setTypeface(tf);
        if (terminalBackground != null) {
            view.setBackgroundImage(terminalBackground);
            view.setBackgroundAlpha(terminalBackgroundAlpha);
        }
        view.setOnTerminalGestureListener(new TerminalView.OnTerminalGestureListener() {
            @Override
            public void onDoubleTap() {
                toggleImmersiveMode();
            }

            @Override
            public void onThreeFingerSwipe(int direction) {
                if (direction > 0) {
                    switchToNextContainer();
                } else {
                    switchToPreviousContainer();
                }
            }

            @Override
            public void onCursorMoveRequest(int column, int row) {
                if (container == activeContainer) {
                    moveCursorToCell(column, row);
                }
            }
            
            @Override
            public void onTerminalTap(float x, float y) {
                // 点击终端时请求焦点并显示软键盘
                inputCommand.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(inputCommand, InputMethodManager.SHOW_IMPLICIT);
                }
            }
            
            @Override
            public void onTerminalLongPress(float x, float y) {
                // 长按时显示上下文菜单，提供复制粘贴等功能
                showTerminalContextMenu(container, x, y);
            }

            @Override
            public void onTerminalKeyDown(int keyCode, KeyEvent event) {
                if (container != activeContainer) {
                    setActiveContainer(container);
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        dispatchInput("\r");
                    } else if (keyCode == KeyEvent.KEYCODE_TAB) {
                        if (event.isShiftPressed()) {
                            if (activeContainer != null && activeContainer.view != null) {
                                activeContainer.view.requestFocus();
                            }
                        } else {
                            if (!keypadVisible) {
                                toggleKeypadVisibility();
                            }
                        }
                        return;
                    } else {
                        String seq = getAnsiSequence(keyCode, event);
                        if (seq != null) {
                            dispatchInput(seq);
                        }
                    }
                }
            }
        });
        
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
        if (id >= nextContainerId) {
            nextContainerId = id + 1;
        }
        return container;
    }

    private void syncSessionsFromContainers() {
        for (TerminalContainer container : containers) {
            if (container.session == null) {
                continue;
            }
            TerminalSession session = container.session;
            SessionInfo existing = findSessionInfoForHost(session.getHost(), session.getPort(), session.getUsername());
            if (existing != null && existing.id != container.id) {
                continue;
            }
            SessionManager.getInstance().upsertSession(
                new SessionInfo(
                    container.id,
                    container.name,
                    session.getHost(),
                    session.getPort(),
                    session.getUsername(),
                    session.getPassword(),
                    session.getAuthType(),
                    session.getKeyPath(),
                    container.connected
                ),
                session
            );
        }
    }
    private void restoreSessions() {
        List<SessionInfo> sessions = SessionManager.getInstance().getSessions();
        if (sessions.isEmpty()) return;

        for (SessionInfo info : sessions) {
             if (hostScoped && !matchesScopedInfo(info)) {
                 continue;
             }
             boolean exists = false;
             for (TerminalContainer c : containers) {
                 if (c.id == info.id) {
                     exists = true;
                     break;
                 }
             }
             if (!exists) {
                 createContainerInternal(info.name, "Default", info.id, buildParamsFromInfo(info));
             }
        }
    }
    
    private void showTerminalContextMenu(TerminalContainer container, float x, float y) {
        PopupMenu popup = new PopupMenu(this, findViewById(android.R.id.content), Gravity.CENTER);
        popup.getMenu().add("复制").setOnMenuItemClickListener(item -> {
            copyTerminalSelection(container);
            return true;
        });
        popup.getMenu().add("粘贴").setOnMenuItemClickListener(item -> {
            pasteFromClipboard();
            return true;
        });
        popup.getMenu().add("全选").setOnMenuItemClickListener(item -> {
            selectAllTerminalText(container);
            return true;
        });
        popup.show();
    }
    
    private void copyTerminalSelection(TerminalContainer container) {
        if (container != null && container.view != null) {
            String content = container.view.getTerminalContent();
            if (content != null && !content.isEmpty()) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Terminal Content", content);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "已复制 " + content.length() + " 个字符", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "终端内容为空", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "没有活动的终端", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void pasteFromClipboard() {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                android.content.ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    CharSequence text = clipData.getItemAt(0).getText();
                    if (text != null) {
                        inputCommand.pasteText(text.toString());
                        Toast.makeText(this, "已粘贴 " + text.length() + " 个字符", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "粘贴失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void selectAllTerminalText(TerminalContainer container) {
        Toast.makeText(this, "全选功能待实现", Toast.LENGTH_SHORT).show();
    }
    
    private void showKeyboardOptionsDialog() {
        PopupMenu popup = new PopupMenu(this, btnShowKeyboardOptions);
        popup.getMenu().add("键盘高度").setOnMenuItemClickListener(item -> {
            showKeyboardHeightDialog();
            return true;
        });
        popup.getMenu().add("键盘布局").setOnMenuItemClickListener(item -> {
            showKeyboardLayoutDialog();
            return true;
        });
        popup.getMenu().add("重置键盘").setOnMenuItemClickListener(item -> {
            resetKeyboardLayout();
            return true;
        });
        popup.show();
    }
    
    private void showKeyboardHeightDialog() {
        // 创建简单的对话框让用户选择键盘高度
        String[] options = {"紧凑", "标准", "宽松"};
        int currentHeight = terminalPrefs.getInt("keyboard_height_option", 1); // 默认为标准
        
        new AlertDialog.Builder(this)
            .setTitle("键盘高度")
            .setSingleChoiceItems(options, currentHeight, (dialog, which) -> {
                terminalPrefs.edit().putInt("keyboard_height_option", which).apply();
                dialog.dismiss();
                Toast.makeText(this, "已更新键盘高度设置", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void showKeyboardLayoutDialog() {
        // 创建键盘布局选择对话框
        String[] layouts = {"标准", "编程", "服务器管理"};
        int currentLayout = terminalPrefs.getInt("keyboard_layout_option", 0);
        
        new AlertDialog.Builder(this)
            .setTitle("键盘布局")
            .setSingleChoiceItems(layouts, currentLayout, (dialog, which) -> {
                terminalPrefs.edit().putInt("keyboard_layout_option", which).apply();
                keypadView.setLayoutMode(which);
                dialog.dismiss();
                // 这里可以重新加载键盘布局
                Toast.makeText(this, "已更新键盘布局: " + layouts[which], Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void resetKeyboardLayout() {
        // 重置键盘布局到默认状态
        new AlertDialog.Builder(this)
            .setTitle("重置键盘")
            .setMessage("确定要重置键盘布局到默认状态吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                // 这里可以重置键盘的自定义设置
                terminalPrefs.edit().putInt("keyboard_layout_option", 0).apply();
                keypadView.setLayoutMode(0);
                Toast.makeText(this, "键盘布局已重置", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void applyKeyboardHeightSetting() {
        // 获取键盘高度设置
        int heightOption = terminalPrefs.getInt("keyboard_height_option", 1); // 默认为标准
        
        // 获取当前键盘参数
        ViewGroup.LayoutParams params = keypadView.getLayoutParams();
        
        // 根据设置调整键盘高度
        switch (heightOption) {
            case 0: // 紧凑
                params.height = (int) (getResources().getDisplayMetrics().density * 100); // 100dp
                break;
            case 1: // 标准
                params.height = (int) (getResources().getDisplayMetrics().density * 140); // 140dp
                break;
            case 2: // 宽松
                params.height = (int) (getResources().getDisplayMetrics().density * 180); // 180dp
                break;
            default:
                params.height = (int) (getResources().getDisplayMetrics().density * 140); // 默认标准
                break;
        }
        
        keypadView.setLayoutParams(params);
    }

    private void closeContainer(TerminalContainer container) {
        containers.remove(container);
        visibleContainers.remove(container);
        containerHost.removeView(container.view);
        TerminalSession session = container.session;
        if (session != null && container.sessionListener != null) {
            session.removeListener(container.sessionListener);
            container.sessionListener = null;
        }
        SessionManager.getInstance().removeSession(container.id);
        if (session != null && !hasOtherContainersForSession(session, container)) {
            removeSessionEntriesForSession(session);
            session.disconnect();
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
        updateTerminalTitle();
        updateTerminalEmptyState();
        syncConnectionParamsFromContainer(container);
        
        // 切换容器时确保输入框获得焦点
        if (inputCommand != null) {
            inputCommand.requestFocus();
        }
    }

    private void updateTerminalEmptyState() {
        if (terminalEmptyState == null || containerHost == null) return;
        boolean showEmpty = containers.isEmpty();
        terminalEmptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (terminalTitle != null && showEmpty) {
            terminalTitle.setText(getString(R.string.nav_terminal_title));
        }
    }

    private void updateTerminalTitle() {
        if (terminalTitle == null) return;
        if (activeContainer == null) {
            terminalTitle.setText(getString(R.string.nav_terminal_title));
            return;
        }
        String label = activeContainer.name;
        if (TextUtils.isEmpty(label)) {
            label = getString(R.string.nav_terminal_title);
        }
        terminalTitle.setText(label);
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
        recordInputHistory(container, send);
        if (localEcho && container.view != null) {
            container.view.append(send);
        }
        if (container.session != null) {
            container.session.write(send);
        }
    }

    private void recordInputHistory(TerminalContainer container, String text) {
        if (container == null || text == null || text.isEmpty()) return;
        if (text.indexOf('\u001b') >= 0) {
            return;
        }
        StringBuilder buffer = container.inputBuffer;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                String cmd = buffer.toString().trim();
                if (!cmd.isEmpty()) {
                    addCommandHistory(container, cmd);
                }
                buffer.setLength(0);
            } else if (c == 0x7f) {
                int len = buffer.length();
                if (len > 0) {
                    buffer.deleteCharAt(len - 1);
                }
            } else if (c >= 0x20) {
                buffer.append(c);
            }
        }
    }

    private void addCommandHistory(TerminalContainer container, String command) {
        if (container.commandHistory.size() >= MAX_HISTORY) {
            container.commandHistory.remove(0);
        }
        container.commandHistory.add(new CommandEntry(command, System.currentTimeMillis()));
    }

    private void showCommandHistoryTimeline() {
        TerminalContainer container = activeContainer;
        if (container == null) return;
        if (container.commandHistory.isEmpty()) {
            Toast.makeText(this, "暂无命令历史", Toast.LENGTH_SHORT).show();
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        List<CommandEntry> entries = container.commandHistory;
        List<CommandEntry> displayEntries = new ArrayList<>();
        List<String> display = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            CommandEntry entry = entries.get(i);
            displayEntries.add(entry);
            display.add(format.format(new Date(entry.timestamp)) + "  " + entry.command);
        }
        new AlertDialog.Builder(this)
            .setTitle("命令历史")
            .setItems(display.toArray(new String[0]), (dialog, which) -> {
                CommandEntry entry = displayEntries.get(which);
                if (entry != null) {
                    dispatchInput(entry.command + "\r");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void moveCursorToCell(int column, int row) {
        TerminalContainer container = activeContainer;
        if (container == null || container.view == null) return;
        TerminalView view = container.view;
        if (view.getEmulator() == null) return;
        int currentCol = view.getEmulator().getCursorX();
        int currentRow = view.getEmulator().getCursorY();
        int dx = column - currentCol;
        int dy = row - currentRow;
        sendCursorRelative(dx, dy);
    }

    private void sendCursorRelative(int dx, int dy) {
        if (dx > 0) {
            dispatchInput("\033[" + dx + "C");
        } else if (dx < 0) {
            dispatchInput("\033[" + (-dx) + "D");
        }
        if (dy > 0) {
            dispatchInput("\033[" + dy + "B");
        } else if (dy < 0) {
            dispatchInput("\033[" + (-dy) + "A");
        }
    }

    private void toggleImmersiveMode() {
        immersiveMode = !immersiveMode;
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                if (immersiveMode) {
                    controller.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                }
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (immersiveMode) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            decor.setSystemUiVisibility(flags);
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
                    SessionInfo info = findSessionInfoById(id);
                    ConnectionParams params = info != null ? buildParamsFromInfo(info) : new ConnectionParams();
                    if (hostScoped && !matchesScopedInfo(info)) {
                        continue;
                    }
                    createContainerInternal(name, group, id, params);
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

        // Do not auto-create a container; allow empty state to guide user.

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
    public void onBackPressed() {
        if (!containers.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Exit Session?")
                .setMessage("Do you want to disconnect all sessions or keep them running in background?")
                .setPositiveButton("Disconnect", (dialog, which) -> super.onBackPressed())
                .setNegativeButton("Background", (dialog, which) -> {
                    // Just finish the activity to return to previous screen (e.g. HostDetail),
                    // but keep sessions alive (handled in onDestroy)
                    finish();
                })
                .setNeutralButton("Cancel", null)
                .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        long focusId = intent.getLongExtra("focus_container_id", -1);
        if (focusId != -1) {
            for (TerminalContainer c : containers) {
                if (c.id == focusId) {
                    setActiveContainer(c);
                    return;
                }
            }
            SessionInfo info = null;
            for (SessionInfo s : SessionManager.getInstance().getSessions()) {
                if (s.id == focusId) {
                    info = s;
                    break;
                }
            }
            if (info != null) {
                hostname = info.hostname;
                port = info.port;
                username = info.username;
                password = info.password;
                authType = info.authType;
                keyPath = info.keyPath;
                TerminalContainer newContainer = createContainerInternal(info.name, currentGroup, info.id, buildParamsFromInfo(info));
                setActiveContainer(newContainer);
                refreshVisibleContainers();
                return;
            }
        }
        
        String newHostname = intent.getStringExtra("hostname");
        if (!TextUtils.isEmpty(newHostname)) {
            // Check if session exists
            boolean found = false;
            int newPort = intent.getIntExtra("port", 22);
            String newUsername = intent.getStringExtra("username");
            hostScoped = true;
            scopedHost = newHostname;
            scopedUser = newUsername;
            scopedPort = newPort;
            
            for (TerminalContainer c : containers) {
                 if (c.session != null && 
                     newHostname.equals(c.session.getHost()) && 
                     newPort == c.session.getPort() &&
                     (newUsername == null || newUsername.equals(c.session.getUsername()))) {
                     setActiveContainer(c);
                     found = true;
                     break;
                 }
            }
            
        if (!found) {
             SessionInfo info = findSessionInfoForHost(newHostname, newPort, newUsername);
             if (info != null) {
                 TerminalContainer restored = ensureContainerForSession(info);
                 if (restored != null) {
                     setActiveContainer(restored);
                     refreshVisibleContainers();
                     return;
                 }
             }
             // Update connection params
             hostname = newHostname;
             port = newPort;
             username = newUsername;
             password = intent.getStringExtra("password");
             authType = intent.getIntExtra("auth_type", 0);
             keyPath = intent.getStringExtra("key_path");
                 
             // Create new container
             long newId = nextContainerId++;
             TerminalContainer newContainer = createContainerInternal(hostname, currentGroup, newId, buildParamsFromCurrent());
             setActiveContainer(newContainer);
             refreshVisibleContainers();
        }
    }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            // Do NOT clear sessions here to allow background persistence
            // SessionManager.getInstance().clearSessions();
            for (TerminalContainer container : containers) {
                // Detach listeners but keep session alive
                if (container.session != null && container.sessionListener != null) {
                    container.session.removeListener(container.sessionListener);
                    container.sessionListener = null;
                }
            }
        }
        if (prefListener != null) {
            terminalPrefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }

    // --- 菜单与选项 ---

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        android.view.SubMenu fontMenu = menu.addSubMenu("快速字体");
        fontMenu.add(0, 1201, 0, "字体: 小");
        fontMenu.add(0, 1202, 0, "字体: 中");
        fontMenu.add(0, 1203, 0, "字体: 大");
        fontMenu.add(0, 1204, 0, "字体: 特大");
        fontMenu.add(0, 1205, 0, "字体: 等宽");
        fontMenu.add(0, 1206, 0, "字体: 系统");
        android.view.SubMenu themeMenu = menu.addSubMenu("快速配色");
        themeMenu.add(0, 2201, 0, "配色: 默认");
        themeMenu.add(0, 2202, 0, "配色: Solarized Dark");
        themeMenu.add(0, 2203, 0, "配色: Solarized Light");
        themeMenu.add(0, 2204, 0, "配色: Monokai");
        themeMenu.add(0, 2205, 0, "配色: Dracula");
        themeMenu.add(0, 2206, 0, "配色: Nord");
        themeMenu.add(0, 2207, 0, "配色: Gruvbox");
        themeMenu.add(0, 2208, 0, "配色: Termius");
        menu.add(0, 1100, 0, "终端设置...");
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
            case 1100:
                Intent settingsIntent = new Intent(this, SettingsDetailActivity.class);
                settingsIntent.putExtra("page", "terminal");
                startActivity(settingsIntent);
                break;
            case 1201: applyFontSize(24); break;
            case 1202: applyFontSize(36); break;
            case 1203: applyFontSize(48); break;
            case 1204: applyFontSize(60); break;
            case 1205:
                currentFontFamily = 0;
                terminalPrefs.edit().putInt(PREF_FONT_FAMILY, currentFontFamily).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 1206:
                currentFontFamily = 1;
                terminalPrefs.edit().putInt(PREF_FONT_FAMILY, currentFontFamily).apply();
                applyDisplayPreferencesToAllViews();
                break;
            case 2201: applyColorScheme(SCHEME_DEFAULT); break;
            case 2202: applyColorScheme(SCHEME_SOLARIZED_DARK); break;
            case 2203: applyColorScheme(SCHEME_SOLARIZED_LIGHT); break;
            case 2204: applyColorScheme(SCHEME_MONOKAI); break;
            case 2205: applyColorScheme(THEME_DRACULA); break;
            case 2206: applyColorScheme(THEME_NORD); break;
            case 2207: applyColorScheme(THEME_GRUVBOX); break;
            case 2208: applyColorScheme(THEME_TERMIUS); break;
            case 2010: showThemeConfigDialog(); break;
            case 2011: showImportThemeDialog(); break;
            case 2012: showExportThemeDialog(); break;
            case 3002:
                toggleKeypadVisibility();
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
        terminalPrefs.edit()
            .putInt("font_size_index", size == 24 ? 0 : size == 36 ? 1 : size == 48 ? 2 : 3)
            .putInt(PREF_FONT_SIZE_PX, size)
            .apply();
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
        else if (scheme == THEME_TERMIUS) idx = 4;
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
        TerminalSession.SessionListener sessionListener;
        TerminalEmulator emulator;
        TerminalView view;
        StringBuilder inputBuffer;
        List<CommandEntry> commandHistory;
    }

    private static class CommandEntry {
        private final String command;
        private final long timestamp;

        private CommandEntry(String command, long timestamp) {
            this.command = command;
            this.timestamp = timestamp;
        }
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
                SessionManager.getInstance().updateSession(container.id, true);
                container.view.append("Connected.\r\n");
            containerAdapter.notifyDataSetChanged();
            updatePersistentNotification();
            
            // 开始会话日志记录
            if (sessionLoggingEnabled && container.session != null) {
                startSessionLogging(container.session.getHost(), 
                    container.session.getPort(), container.session.getUsername());
            }
            
            if (container == activeContainer) {
                maybeRunInitialCommand(container);
            }
        });
    }

    @Override
    public void onDisconnected() {
            runOnUiThread(() -> {
                container.connected = false;
                SessionManager.getInstance().updateSession(container.id, false);
                container.view.append("Disconnected.\r\n");
            containerAdapter.notifyDataSetChanged();
            updatePersistentNotification();
            
            // 停止会话日志记录
            if (sessionLoggingEnabled) {
                stopSessionLogging();
            }
        });
    }

    @Override
    public void onDataReceived(String data) {
        if (container.view != null) {
            container.view.append(data);
        }
        
        // 记录会话输出到日志
        if (sessionLoggingEnabled) {
            logSessionOutput(data);
        }
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            container.connected = false;
            SessionManager.getInstance().updateSession(container.id, false);
            container.view.append("Error: " + message + "\r\n");
            containerAdapter.notifyDataSetChanged();
        });
    }
}

    private class ContainerAdapter extends RecyclerView.Adapter<ContainerAdapter.ContainerViewHolder> {

        @NonNull
        @Override
        public ContainerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int padding = (int) (8 * parent.getResources().getDisplayMetrics().density);
            int dotSize = (int) (7 * parent.getResources().getDisplayMetrics().density);
            int minWidth = (int) (130 * parent.getResources().getDisplayMetrics().density);
            int statusPadH = (int) (5 * parent.getResources().getDisplayMetrics().density);
            int statusPadV = (int) (2 * parent.getResources().getDisplayMetrics().density);

            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER_VERTICAL);
            layout.setPadding(padding, padding, padding, padding);
            layout.setMinimumWidth(minWidth);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, padding, 0);
            layout.setLayoutParams(lp);

            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            ImageView statusView = new ImageView(parent.getContext());
            statusView.setImageResource(R.drawable.bg_status_indicator);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotParams.setMarginEnd(padding / 2);
            row.addView(statusView, dotParams);

            TextView titleView = new TextView(parent.getContext());
            titleView.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.terminal_key_text));
            titleView.setTextSize(12);
            titleView.setTypeface(null, Typeface.BOLD);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(titleView, titleParams);

            TextView closeView = new TextView(parent.getContext());
            closeView.setText("×");
            closeView.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.terminal_key_text));
            closeView.setTextSize(13);
            closeView.setPadding(padding / 2, 0, 0, 0);
            row.addView(closeView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout rowBottom = new LinearLayout(parent.getContext());
            rowBottom.setOrientation(LinearLayout.HORIZONTAL);
            rowBottom.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bottomParams.topMargin = padding / 3;
            rowBottom.setLayoutParams(bottomParams);

            TextView subView = new TextView(parent.getContext());
            subView.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.terminal_key_text));
            subView.setTextSize(10);
            subView.setSingleLine(true);
            subView.setEllipsize(TextUtils.TruncateAt.END);
            subView.setAlpha(0.7f);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rowBottom.addView(subView, subParams);

            TextView statusText = new TextView(parent.getContext());
            statusText.setTextSize(9);
            statusText.setPadding(statusPadH, statusPadV, statusPadH, statusPadV);
            statusText.setSingleLine(true);
            statusText.setEllipsize(TextUtils.TruncateAt.END);
            rowBottom.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            layout.addView(row);
            layout.addView(rowBottom);

            return new ContainerViewHolder(layout, titleView, subView, closeView, statusView, statusText);
        }

        @Override
        public void onBindViewHolder(@NonNull ContainerViewHolder holder, int position) {
            TerminalContainer container = visibleContainers.get(position);
            String hostLine = buildHostLine(container);
            String title = TextUtils.isEmpty(container.name) ? hostLine : container.name;
            holder.titleView.setText(title);
            if (!TextUtils.isEmpty(hostLine) && !hostLine.equals(title)) {
                holder.subView.setVisibility(View.VISIBLE);
                holder.subView.setText(hostLine);
            } else {
                holder.subView.setVisibility(View.GONE);
                holder.subView.setText("");
            }
            GradientDrawable bg = new GradientDrawable();
            int radius = (int) (14 * holder.itemView.getResources().getDisplayMetrics().density);
            bg.setCornerRadius(radius);
            int activeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.terminal_key_bg_active);
            int inactiveColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.terminal_key_bg);
            bg.setColor(container == activeContainer ? activeColor : inactiveColor);
            int strokeWidth = (int) (1 * holder.itemView.getResources().getDisplayMetrics().density);
            holder.itemView.setBackground(bg);
            int textColor = ContextCompat.getColor(holder.itemView.getContext(),
                    container == activeContainer ? R.color.terminal_key_text_active : R.color.terminal_key_text);
            bg.setStroke(container == activeContainer ? strokeWidth : 0, textColor);
            holder.titleView.setTextColor(textColor);
            holder.subView.setTextColor(textColor);
            holder.subView.setAlpha(container == activeContainer ? 0.85f : 0.7f);
            holder.closeView.setTextColor(textColor);
            int dotColor = ContextCompat.getColor(holder.itemView.getContext(),
                    container.connected ? R.color.status_success : R.color.status_neutral);
            holder.statusView.setImageTintList(android.content.res.ColorStateList.valueOf(dotColor));
            String statusText = holder.itemView.getContext().getString(
                container.connected ? R.string.session_status_connected : R.string.session_status_disconnected);
            holder.statusText.setText(statusText);
            holder.statusText.setTextColor(dotColor);
            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(radius);
            int badgeBg = ContextCompat.getColor(holder.itemView.getContext(), R.color.terminal_termius_surface_variant);
            badge.setColor(badgeBg);
            holder.statusText.setBackground(badge);
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
            TextView subView;
            TextView closeView;
            ImageView statusView;
            TextView statusText;

            ContainerViewHolder(@NonNull View itemView, TextView titleView, TextView subView, TextView closeView, ImageView statusView, TextView statusText) {
                super(itemView);
                this.titleView = titleView;
                this.subView = subView;
                this.closeView = closeView;
                this.statusView = statusView;
                this.statusText = statusText;
            }
        }
    }

    private String buildHostLine(TerminalContainer container) {
        if (container == null) return "";
        String host = null;
        int port = 0;
        String user = null;
        if (container.session != null) {
            host = container.session.getHost();
            port = container.session.getPort();
            user = container.session.getUsername();
        } else {
            SessionInfo info = findSessionInfoById(container.id);
            if (info != null) {
                host = info.hostname;
                port = info.port;
                user = info.username;
            }
        }
        if (TextUtils.isEmpty(host)) return "";
        if (TextUtils.isEmpty(user)) {
            return host + ":" + port;
        }
        return user + "@" + host + ":" + port;
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
        if (source == null || source.session == null) return;
        String name = source.name + " 副本";
        TerminalContainer container = createContainerInternal(name, source.group, nextContainerId++, buildParamsForContainer(source));
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
            if (container == null) return;
            ConnectionParams params = buildParamsForContainer(container);
            if (TextUtils.isEmpty(params.host) || TextUtils.isEmpty(params.user)) {
                Toast.makeText(this, "缺少主机信息，无法重连", Toast.LENGTH_SHORT).show();
                return;
            }

            TerminalSession oldSession = container.session;
            List<TerminalContainer> targets = new ArrayList<>();
            if (oldSession != null) {
                for (TerminalContainer c : containers) {
                    if (c.session == oldSession) {
                        targets.add(c);
                    }
                }
                for (TerminalContainer c : targets) {
                    if (c.sessionListener != null) {
                        oldSession.removeListener(c.sessionListener);
                        c.sessionListener = null;
                    }
                }
                oldSession.disconnect();
            } else {
                targets.add(container);
            }

            TerminalSession session = new TerminalSession();
            session.setHostKeyVerifier(createHostKeyVerifier());
            session.connect(params.host, params.port, params.user, params.password, params.authType, params.keyPath);

            for (TerminalContainer c : targets) {
                c.connected = false;
                c.session = session;
                if (c.view != null && c.emulator != null) {
                    c.view.attachEmulator(c.emulator);
                }
                ContainerSessionListener listener = new ContainerSessionListener(c);
                session.addListener(listener);
                c.sessionListener = listener;
                SessionManager.getInstance().upsertSession(
                    new SessionInfo(c.id, c.name, params.host, params.port, params.user, params.password, params.authType, params.keyPath, false),
                    session
                );
                if (c.view != null) {
                    c.view.append("Reconnecting...\r\n");
                }
            }
            containerAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Toast.makeText(this, "重连失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectContainer(TerminalContainer container) {
        if (container.session != null) {
            container.session.disconnect();
            container.connected = false;
            if (container.view != null) {
                container.view.append("Disconnected by user.\r\n");
            }
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
        defaultActions.add(new TerminalAction("清屏", CommandConstants.CMD_CLEAR, false));
        defaultActions.add(new TerminalAction("列出目录", CommandConstants.CMD_LS_LA, false));
        defaultActions.add(new TerminalAction("当前路径", CommandConstants.CMD_PWD, false));
        defaultActions.add(new TerminalAction("当前用户", CommandConstants.CMD_WHOAMI, false));
        defaultActions.add(new TerminalAction("Docker状态", CommandConstants.CMD_DOCKER_PS, false));
        defaultActions.add(new TerminalAction("内存使用", CommandConstants.CMD_FREE_M, false));
        defaultActions.add(new TerminalAction("磁盘使用", CommandConstants.CMD_DF_H, false));
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

    private List<TerminalAction> buildContextActions(TerminalContainer container) {
        List<TerminalAction> actions = new ArrayList<>();
        String context = getLastCommandContext(container);
        if (context == null || context.isEmpty()) {
            return actions;
        }
        String lower = context.toLowerCase();
        if (lower.contains("git")) {
            actions.add(new TerminalAction("Git: 状态", CommandConstants.CMD_GIT_STATUS, false));
            actions.add(new TerminalAction("Git: 差异", CommandConstants.CMD_GIT_DIFF, false));
            actions.add(new TerminalAction("Git: 日志", CommandConstants.CMD_GIT_LOG_ONELINE_10, false));
            actions.add(new TerminalAction("Git: 分支", CommandConstants.CMD_GIT_BRANCH_VV, false));
        }
        if (lower.contains("docker") || lower.contains("compose")) {
            actions.add(new TerminalAction("Docker: 运行中容器", CommandConstants.CMD_DOCKER_PS, false));
            actions.add(new TerminalAction("Docker: 所有容器", CommandConstants.CMD_DOCKER_PS_ALL, false));
            actions.add(new TerminalAction("Docker: 镜像列表", CommandConstants.CMD_DOCKER_IMAGES, false));
            actions.add(new TerminalAction("Docker: 资源统计", CommandConstants.CMD_DOCKER_STATS, false));
        }
        if (lower.contains("kubectl") || lower.contains("k8s") || lower.contains("helm")) {
            actions.add(new TerminalAction("K8s: Pod 列表", CommandConstants.CMD_KUBECTL_GET_PODS_ALL, false));
            actions.add(new TerminalAction("K8s: Service 列表", CommandConstants.CMD_KUBECTL_GET_SVC_ALL, false));
            actions.add(new TerminalAction("K8s: 节点列表", CommandConstants.CMD_KUBECTL_GET_NODES, false));
        }
        if (lower.contains("tmux")) {
            actions.add(new TerminalAction("Tmux: 会话列表", CommandConstants.CMD_TMUX_LS, false));
            actions.add(new TerminalAction("Tmux: 附加会话", CommandConstants.CMD_TMUX_ATTACH_PREFIX, false));
        }
        return actions;
    }

    private String getLastCommandContext(TerminalContainer container) {
        if (container == null) return null;
        if (container.commandHistory != null && !container.commandHistory.isEmpty()) {
            CommandEntry entry = container.commandHistory.get(container.commandHistory.size() - 1);
            if (entry != null && entry.command != null) {
                return entry.command.trim();
            }
        }
        if (container.inputBuffer != null && container.inputBuffer.length() > 0) {
            return container.inputBuffer.toString().trim();
        }
        return null;
    }

    private void showCommandPanel() {
        TerminalSession session = getActiveSession();
        if (session == null) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TerminalAction> actions = new ArrayList<>();
        actions.addAll(defaultActions);
        actions.addAll(buildContextActions(activeContainer));
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
        
        // 记录命令到历史
        if (commandHistoryManager != null) {
            commandHistoryManager.recordCommand(command);
        }
        
        String eol = enterNewline ? "\n" : "\r";
        String cmd = command;
        if (!cmd.endsWith("\n") && !cmd.endsWith("\r")) {
            cmd += eol;
        }
        dispatchInput(cmd);
        
        // 隐藏自动补全
        hideAutocomplete();
    }

    // ==================== 命令自动补全功能 ====================
    
    private void onAutocompleteSelected(String command) {
        inputCommand.setText(command);
        inputCommand.setSelection(command.length());
        hideAutocomplete();
    }

    private void setupAutocompleteView() {
        // 创建自动补全容器
        autocompleteContainer = new android.widget.LinearLayout(this);
        autocompleteContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        autocompleteContainer.setBackgroundColor(Color.WHITE);
        autocompleteContainer.setElevation(8);
        autocompleteContainer.setVisibility(View.GONE);
        
        // 添加到布局（在输入框上方）
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM;
        params.bottomMargin = 200; // 留出键盘空间
        
        containerHost.addView(autocompleteContainer, params);
        
        // 创建 RecyclerView
        autocompleteRecyclerView = new RecyclerView(this);
        autocompleteRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        autocompleteAdapter = new com.orcterm.ui.adapter.AutocompleteAdapter();
        autocompleteAdapter.setOnSuggestionClickListener(suggestion -> {
            // 点击建议时，填充到输入框
            onAutocompleteSelected(suggestion.command);
        });
        autocompleteRecyclerView.setAdapter(autocompleteAdapter);
        
        autocompleteContainer.addView(autocompleteRecyclerView);
        
        // 设置最大高度
        autocompleteRecyclerView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            400 // 最大高度 400dp
        ));
    }
    
    private void showAutocompleteSuggestions(String prefix) {
        if (!commandCompletionEnabled || commandHistoryManager == null || prefix == null || prefix.isEmpty()) {
            hideAutocomplete();
            return;
        }
        
        List<CommandHistoryManager.CommandSuggestion> suggestions = 
            commandHistoryManager.getSuggestions(prefix);
        
        if (suggestions.isEmpty()) {
            hideAutocomplete();
            return;
        }
        
        autocompleteAdapter.setSuggestions(suggestions);
        autocompleteContainer.setVisibility(View.VISIBLE);
    }
    
    private void hideAutocomplete() {
        if (autocompleteContainer != null) {
            autocompleteContainer.setVisibility(View.GONE);
        }
    }
    
    // ==================== 会话日志功能 ====================
    
    private void startSessionLogging(String hostname, int port, String username) {
        if (sessionLoggingEnabled && sessionLogManager != null) {
            sessionLogManager.startSession(hostname, port, username);
        }
    }
    
    private void stopSessionLogging() {
        if (sessionLogManager != null) {
            sessionLogManager.endSession();
        }
    }
    
    private void logSessionOutput(String data) {
        if (sessionLoggingEnabled && sessionLogManager != null) {
            sessionLogManager.writeLog(data);
        }
    }
    
    private void showSessionLogsDialog() {
        if (sessionLogManager == null) return;
        
        sessionLogManager.getAllLogs(logs -> {
            runOnUiThread(() -> {
                if (logs.isEmpty()) {
                    Toast.makeText(TerminalActivity.this, "暂无会话日志", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 创建日志列表对话框
                String[] items = new String[logs.size()];
                for (int i = 0; i < logs.size(); i++) {
                    SessionLogManager.SessionLog log = logs.get(i);
                    SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                    items[i] = log.hostInfo + "\n" + sdf.format(new Date(log.startTime)) + 
                        " (" + (log.size / 1024) + " KB, " + log.lineCount + " 行)";
                }
                
                new AlertDialog.Builder(this)
                    .setTitle("会话日志")
                    .setItems(items, (dialog, which) -> {
                        // 查看选中的日志
                        showLogContentDialog(logs.get(which));
                    })
                    .setNegativeButton("关闭", null)
                    .setNeutralButton("清理全部", (dialog, which) -> {
                        new AlertDialog.Builder(this)
                            .setTitle("确认清理")
                            .setMessage("确定要删除所有会话日志吗？")
                            .setPositiveButton("删除", (d, w) -> {
                                sessionLogManager.clearAllLogs(deletedCount -> {
                                    runOnUiThread(() -> Toast.makeText(TerminalActivity.this, 
                                        "已清理 " + deletedCount + " 个日志文件", Toast.LENGTH_SHORT).show());
                                });
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    })
                    .show();
            });
        });
    }
    
    private void showLogContentDialog(SessionLogManager.SessionLog log) {
        sessionLogManager.readLogContent(log.logFilePath, 1000, lines -> {
            runOnUiThread(() -> {
                StringBuilder content = new StringBuilder();
                for (String line : lines) {
                    content.append(line).append("\n");
                }
                
                // 创建可滚动的文本视图
                android.widget.ScrollView scrollView = new android.widget.ScrollView(TerminalActivity.this);
                TextView textView = new TextView(TerminalActivity.this);
                textView.setText(content.toString());
                textView.setTextSize(12);
                textView.setPadding(16, 16, 16, 16);
                textView.setTypeface(Typeface.MONOSPACE);
                scrollView.addView(textView);
                
                new AlertDialog.Builder(TerminalActivity.this)
                    .setTitle(log.hostInfo)
                    .setView(scrollView)
                    .setPositiveButton("关闭", null)
                    .setNegativeButton("删除", (d, w) -> {
                        sessionLogManager.deleteLog(log.logFilePath, () -> {
                            runOnUiThread(() -> Toast.makeText(TerminalActivity.this, 
                                "日志已删除", Toast.LENGTH_SHORT).show());
                        });
                    })
                    .show();
            });
        });
    }

    private void applyDisplayPreferencesToAllViews() {
        for (TerminalContainer container : containers) {
            TerminalView v = container.view;
            if (v == null) continue;
            v.setColorScheme(getEffectiveScheme());
            v.setLineHeightMultiplier(currentLineHeight);
            v.setLetterSpacing(currentLetterSpacing);
            Typeface base = currentFontFamily == 0 ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
            Typeface tf = Typeface.create(base, currentFontWeight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
            v.setTypeface(tf);
            v.setSelectionColor(currentSelectionColor);
            v.setSearchHighlightColor(currentSearchHighlightColor);
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

    private void showThemeConfigDialog() {
        Intent intent = new Intent(this, ThemeEditorActivity.class);
        intent.putExtra("hostname", hostname);
        intent.putExtra("username", username);
        intent.putExtra("port", port);
        intent.putExtra("password", password);
        intent.putExtra("auth_type", authType);
        intent.putExtra("key_path", keyPath);
        startActivity(intent);
    }

    private void applyThemeConfig(ThemeRepository.ThemeConfig config) {
        if (config.ansiColors != null && config.ansiColors.length >= 16) {
            currentScheme = config.ansiColors;
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
        float density = getResources().getDisplayMetrics().scaledDensity;
        int sizePx = Math.max(8, Math.round(config.fontSizeSp * density));
        currentFontSize = sizePx;
        applyDisplayPreferencesToAllViews();
    }

    private void updatePreview(TerminalView preview, ThemeRepository.ThemeConfig config) {
        if (preview == null || config == null) return;
        int[] scheme = buildEffectiveScheme(config);
        preview.setColorScheme(scheme);
        int sizePx = Math.max(8, Math.round(config.fontSizeSp * getResources().getDisplayMetrics().scaledDensity));
        preview.setFontSize(sizePx);
        preview.setLineHeightMultiplier(config.lineHeight);
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
        preview.setShowLineNumbers(true);
        preview.setLineNumberColor(lineNumber);
        preview.setShowBracketMatch(true);
        preview.setBracketMatchColor(bracketMatch);
        preview.setShowHighRiskHighlight(true);
        preview.setHighRiskHighlightColor(highRisk);
        preview.setPreviewContent(buildPreviewText(config));
    }

    private String buildPreviewText(ThemeRepository.ThemeConfig config) {
        String family = config.fontFamily == 0 ? "等宽" : "系统";
        int sizeSp = Math.round(config.fontSizeSp);
        String lineHeight = String.format(java.util.Locale.getDefault(), "%.1f", config.lineHeight);
        String letterSpacing = String.format(java.util.Locale.getDefault(), "%.1f", config.letterSpacing);
        return "orcTerm 预览\n"
            + "字体: " + family + "  大小: " + sizeSp + "sp  粗细: " + config.fontWeight + "\n"
            + "行高: " + lineHeight + "  字距: " + letterSpacing + "\n"
            + "括号匹配: (foo[bar]{baz})\n"
            + "高危命令: rm -rf /\n"
            + "命令示例: ls -la /var/log\n";
    }

    private int[] buildEffectiveScheme(ThemeRepository.ThemeConfig config) {
        int[] scheme = new int[16];
        if (config.ansiColors != null && config.ansiColors.length >= 16) {
            System.arraycopy(config.ansiColors, 0, scheme, 0, 16);
        } else {
            System.arraycopy(THEME_TERMIUS, 0, scheme, 0, 16);
        }
        scheme[0] = config.backgroundColor;
        scheme[7] = config.foregroundColor;
        scheme[15] = config.foregroundColor;
        return ensureReadableScheme(scheme);
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

    private void applyThemeJsonFromPrefs() {
        String json = terminalPrefs.getString(PREF_THEME_JSON, null);
        if (json == null || json.isEmpty()) return;
        try {
            Object parsed = new org.json.JSONTokener(json).nextValue();
            if (parsed instanceof JSONObject) {
                JSONObject obj = (JSONObject) parsed;
                if (themeMatchesHost(obj)) {
                    applyThemeJsonObject(obj);
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean themeMatchesHost(JSONObject obj) {
        JSONObject bindings = null;
        JSONObject advanced = obj.optJSONObject("advanced");
        if (advanced != null) {
            bindings = advanced.optJSONObject("bindings");
        }
        if (bindings == null) {
            bindings = obj.optJSONObject("bindings");
        }
        JSONArray hosts = bindings != null ? bindings.optJSONArray("hosts") : null;
        if (hosts == null || hosts.length() == 0) return true;
        if (TextUtils.isEmpty(hostname)) return false;
        for (int i = 0; i < hosts.length(); i++) {
            JSONObject h = hosts.optJSONObject(i);
            if (h == null) continue;
            String hostValue = h.optString("hostname", "");
            int portValue = h.optInt("port", -1);
            String userValue = h.optString("username", "");
            if (!hostValue.isEmpty() && !hostValue.equalsIgnoreCase(hostname)) {
                continue;
            }
            if (portValue > 0 && portValue != port) {
                continue;
            }
            if (!userValue.isEmpty() && (username == null || !userValue.equalsIgnoreCase(username))) {
                continue;
            }
            return true;
        }
        return false;
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
            applyColorScheme(ansi);
            saveCurrentColorScheme();
        }
        JSONObject cursor = obj.optJSONObject("cursor");
        if (cursor != null) {
            String style = cursor.optString("style", "");
            if (!style.isEmpty()) {
                TerminalView.CursorStyle s = parseCursorStyle(style);
                terminalPrefs.edit().putInt("terminal_cursor_style", s.ordinal()).apply();
            }
            if (cursor.has("blink")) {
                terminalPrefs.edit().putBoolean("terminal_cursor_blink", cursor.optBoolean("blink", true)).apply();
            }
            Integer cursorColor = parseColorSilent(cursor.opt("color"));
            if (cursorColor != null) {
                terminalPrefs.edit().putInt("terminal_cursor_color", cursorColor).apply();
            }
        }
        JSONObject font = obj.optJSONObject("font");
        if (font != null) {
            double sizeSp = font.optDouble("sizeSp", -1);
            if (sizeSp > 0) {
                float density = getResources().getDisplayMetrics().scaledDensity;
                int sizePx = Math.max(8, Math.round((float) sizeSp * density));
                applyFontSize(sizePx);
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
        applyDisplayPreferencesToAllViews();
    }

    private TerminalView.CursorStyle parseCursorStyle(String style) {
        if ("underline".equalsIgnoreCase(style)) return TerminalView.CursorStyle.UNDERLINE;
        if ("beam".equalsIgnoreCase(style)) return TerminalView.CursorStyle.BAR;
        return TerminalView.CursorStyle.BLOCK;
    }

    private void handleThemeAction(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("theme_action");
        if (action == null || action.isEmpty()) return;
        intent.removeExtra("theme_action");
        if ("editor".equals(action)) {
            showThemeConfigDialog();
        } else if ("import".equals(action)) {
            showImportThemeDialog();
        } else if ("export".equals(action)) {
            showExportThemeDialog();
        }
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

    /**
     * 显示导入主题对话框
     */
    private void showImportThemeDialog() {
        android.widget.EditText edit = new android.widget.EditText(this);
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

    /**
     * 从JSON导入主题
     */
    private void importThemeFromJson(String json) {
        try {
            Object parsed = new org.json.JSONTokener(json).nextValue();
            if (parsed instanceof org.json.JSONObject) {
                JSONObject obj = normalizeThemeJsonObject((JSONObject) parsed);
                applyThemeJsonObject(obj);
                terminalPrefs.edit().putString(PREF_THEME_JSON, obj.toString()).apply();
            } else if (parsed instanceof org.json.JSONArray) {
                org.json.JSONArray array = (org.json.JSONArray) parsed;
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
            JSONObject obj = buildThemeJsonFromPalette(colors, "Imported");
            applyThemeJsonObject(obj);
            terminalPrefs.edit().putString(PREF_THEME_JSON, obj.toString()).apply();
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

    private Integer parseColorOptional(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            int color = ((Number) value).intValue();
            if ((color & 0xFF000000) == 0) {
                color |= 0xFF000000;
            }
            return color;
        }
        if (value instanceof String) {
            return parseColorInput((String) value);
        }
        return null;
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
        } catch (JSONException ignored) {}
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
        } catch (JSONException ignored) {}
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
            int styleIndex = terminalPrefs.getInt("terminal_cursor_style", 0);
            String style = styleIndex == TerminalView.CursorStyle.UNDERLINE.ordinal() ? "underline"
                : styleIndex == TerminalView.CursorStyle.BAR.ordinal() ? "beam" : "block";
            cursor.put("style", style);
            cursor.put("blink", terminalPrefs.getBoolean("terminal_cursor_blink", true));
            cursor.put("blinkIntervalMs", 500);
            cursor.put("color", formatColor(terminalPrefs.getInt("terminal_cursor_color", 0xFFFFFFFF)));
            JSONObject vim = new JSONObject();
            vim.put("insert", new JSONObject().put("style", style));
            vim.put("normal", new JSONObject().put("style", "block"));
            vim.put("visual", new JSONObject().put("style", "underline"));
            cursor.put("vimMode", vim);
        } catch (JSONException ignored) {}
        return cursor;
    }

    private JSONObject buildFontJson() {
        JSONObject font = new JSONObject();
        try {
            float density = getResources().getDisplayMetrics().scaledDensity;
            float sizeSp = density == 0 ? 12 : currentFontSize / density;
            font.put("family", currentFontFamily == 0 ? "monospace" : "system");
            font.put("fallback", new JSONArray().put("monospace").put("sans-serif"));
            font.put("sizeSp", Math.max(6, Math.round(sizeSp * 10f) / 10f));
            font.put("lineHeight", currentLineHeight);
            font.put("letterSpacing", currentLetterSpacing);
            font.put("boldWeight", 600);
            font.put("italicStyle", "oblique");
            font.put("ligatures", false);
            font.put("nerdFont", false);
        } catch (JSONException ignored) {}
        return font;
    }

    private JSONObject buildUiJson() {
        JSONObject ui = new JSONObject();
        try {
            JSONObject padding = new JSONObject();
            padding.put("top", 0);
            padding.put("right", 0);
            padding.put("bottom", 0);
            padding.put("left", 0);
            ui.put("padding", padding);
            JSONObject scrollbar = new JSONObject();
            scrollbar.put("visible", true);
            scrollbar.put("width", 2);
            scrollbar.put("color", "#66FFFFFF");
            scrollbar.put("autoHide", true);
            ui.put("scrollbar", scrollbar);
            JSONObject tab = new JSONObject();
            tab.put("active", "#1F2A33");
            tab.put("inactive", "#12161C");
            tab.put("textActive", "#FFFFFF");
            tab.put("textInactive", "#9AA4B2");
            ui.put("tab", tab);
            JSONObject status = new JSONObject();
            status.put("connected", "#4CAF50");
            status.put("connecting", "#FF9800");
            status.put("disconnected", "#F44336");
            ui.put("status", status);
        } catch (JSONException ignored) {}
        return ui;
    }

    private JSONObject buildBehaviorJson() {
        JSONObject behavior = new JSONObject();
        try {
            boolean autoScroll = terminalPrefs.getBoolean("terminal_auto_scroll_output", true);
            boolean smoothScroll = terminalPrefs.getBoolean("terminal_smooth_scroll", true);
            boolean copyOnSelect = terminalPrefs.getBoolean("terminal_copy_on_select", true);
            boolean pasteOnTap = terminalPrefs.getBoolean("terminal_paste_on_tap", false);
            boolean bellAudio = terminalPrefs.getBoolean("terminal_bell_audio", false);
            boolean bellVisual = terminalPrefs.getBoolean("terminal_bell_visual", true);
            behavior.put("autoScrollOnOutput", autoScroll);
            behavior.put("smoothScroll", smoothScroll);
            behavior.put("copyOnSelect", copyOnSelect);
            behavior.put("pasteOnTap", pasteOnTap);
            JSONObject bell = new JSONObject();
            bell.put("audio", bellAudio);
            bell.put("visual", bellVisual);
            behavior.put("bell", bell);
        } catch (JSONException ignored) {}
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
        } catch (JSONException ignored) {}
        return advanced;
    }


    /**
     * 显示导出主题对话框
     */
    private void showExportThemeDialog() {
        JSONObject jsonObject = buildThemeJsonFromPalette(currentScheme, "Custom");
        String jsonExport = jsonObject.toString();
        
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

    private HostKeyVerifier createHostKeyVerifier() {
        return (host, port, fingerprint, status) -> {
            AtomicBoolean result = new AtomicBoolean(false);
            AtomicBoolean completed = new AtomicBoolean(false);
            
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(TerminalActivity.this);
                
                String message;
                
                if (status == 2) { // NOT FOUND
                    builder.setTitle("SSH Host Key Verification");
                    message = "The authenticity of host '" + host + "' can't be established.\n" + 
                              "Fingerprint (SHA256): " + fingerprint + "\n\n" +
                              "Are you sure you want to continue connecting?";
                    builder.setMessage(message);
                    builder.setPositiveButton("Connect", (d, w) -> {
                        result.set(true);
                        synchronized(completed) { completed.set(true); completed.notifyAll(); }
                    });
                    builder.setNegativeButton("Cancel", (d, w) -> {
                         result.set(false);
                         synchronized(completed) { completed.set(true); completed.notifyAll(); }
                    });
                } else if (status == 1) { // MISMATCH
                    builder.setTitle("⚠️ SEVERE SECURITY WARNING");
                    message = "REMOTE HOST IDENTIFICATION HAS CHANGED!\n\n" +
                              "IT IS POSSIBLE THAT SOMEONE IS DOING SOMETHING NASTY!\n" +
                              "Someone could be eavesdropping on you right now (man-in-the-middle attack)!\n" +
                              "It is also possible that the host key has just been changed.\n\n" +
                              "New Fingerprint (SHA256): " + fingerprint + "\n\n" +
                              "Do you want to proceed anyway?";
                    
                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                    builder.setMessage(message);
                    
                    builder.setPositiveButton("Connect Anyway", (d, w) -> {
                        result.set(true);
                        synchronized(completed) { completed.set(true); completed.notifyAll(); }
                    });
                    builder.setNegativeButton("Cancel", (d, w) -> {
                        result.set(false);
                        synchronized(completed) { completed.set(true); completed.notifyAll(); }
                    });
                } else {
                     // Failure or other
                     result.set(false);
                     synchronized(completed) { completed.set(true); completed.notifyAll(); }
                     return;
                }
                
                builder.setCancelable(false);
                AlertDialog dialog = builder.create();
                dialog.show();
                
                if (status == 1) {
                    try {
                        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
                        android.widget.TextView titleView = dialog.findViewById(titleId);
                        if (titleView != null) {
                            titleView.setTextColor(android.graphics.Color.RED);
                        }
                    } catch (Exception ignored) {}
                }
            });
            
            synchronized(completed) {
                while (!completed.get()) {
                    try { completed.wait(); } catch (InterruptedException e) {}
                }
            }
            return result.get();
        };
    }
}
