package com.orcterm.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.orcterm.R;
import com.orcterm.core.session.SessionInfo;
import com.orcterm.core.session.SessionManager;
import com.orcterm.core.terminal.TerminalEmulator;
import com.orcterm.core.terminal.TerminalSession;
import com.orcterm.core.transport.HostKeyVerifier;
import com.orcterm.ui.widget.TerminalInputView;
import com.orcterm.ui.widget.TerminalView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSH terminal screen with a quick key panel and soft keyboard passthrough.
 * Keeps compatibility with terminal theme and keyboard preferences.
 */
public class SshTerminalActivity extends AppCompatActivity {

    private static final String PREFS = "orcterm_prefs";
    private static final String PREF_KEYPAD_VISIBLE = "terminal_keypad_visible";
    private static final String PREF_KEYPAD_MAPPING = "terminal_keypad_mapping";
    private static final String PREF_KEYBOARD_HEIGHT_OPTION = "keyboard_height_option";
    private static final String PREF_KEYBOARD_LAYOUT_OPTION = "keyboard_layout_option";
    private static final String PREF_ENTER_NEWLINE = "terminal_enter_newline";
    private static final String PREF_LOCAL_ECHO = "terminal_local_echo";
    private static final String PREF_AUTO_SCROLL_OUTPUT = "terminal_auto_scroll_output";
    private static final String PREF_SCROLLBACK_LINES = "terminal_scrollback_lines";
    private static final String PREF_BG_URI = "terminal_bg_uri";

    private static final int STATUS_CONNECTED = 0xFF69DB7C;
    private static final int STATUS_CONNECTING = 0xFFFFD166;
    private static final int STATUS_DISCONNECTED = 0xFFFF6B6B;

    private static final String ESC = "\u001b";

    private static final int[] FALLBACK_SCHEME = {
        0xFF151A1E, 0xFFF75F5F, 0xFF7FD962, 0xFFF2C94C,
        0xFF5AA9FF, 0xFFC792EA, 0xFF5ED4F4, 0xFFE6EDF3,
        0xFF5A6B7A, 0xFFFF7070, 0xFF9BE77C, 0xFFF6D06F,
        0xFF7BB9FF, 0xFFD6A7F0, 0xFF7FE3F8, 0xFFFFFFFF
    };

    private enum QuickKeyAction {
        SEND,
        MODIFIER,
        COPY,
        PASTE,
        TOGGLE_IME,
        TOGGLE_PANEL
    }

    private static final class QuickKeySpec {
        final String label;
        final String mapKey;
        final String defaultValue;
        final QuickKeyAction action;

        QuickKeySpec(String label, String mapKey, String defaultValue, QuickKeyAction action) {
            this.label = label;
            this.mapKey = mapKey;
            this.defaultValue = defaultValue;
            this.action = action;
        }
    }

    private TextView hostText;
    private TextView statusText;
    private MaterialButton reconnectButton;
    private MaterialButton disconnectButton;
    private MaterialButton toggleKeypadButton;
    private MaterialButton toggleSoftKeyboardButton;
    private MaterialButton keyboardOptionsButton;
    private View keyboardControlBar;
    private LinearLayout quickKeypadPanel;
    private LinearLayout quickKeyRow1;
    private LinearLayout quickKeyRow2;
    private LinearLayout quickKeyRow3;
    private TerminalView terminalView;
    private TerminalInputView imeInput;

    private TerminalSession session;
    private TerminalEmulator emulator;
    private SharedPreferences terminalPrefs;
    private ThemeRepository themeRepository;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private InputMethodManager inputMethodManager;

    private final Map<String, String> keypadMapping = new HashMap<>();
    private final Map<String, MaterialButton> quickKeyButtons = new HashMap<>();
    private boolean keypadVisible = true;
    private boolean enterNewline = true;
    private boolean localEcho = false;
    private boolean autoScrollOutput = true;
    private Bitmap terminalBackground;

    private int keyboardLayoutMode = 0;
    private boolean modifierCtrl = false;
    private boolean modifierAlt = false;
    private boolean modifierShift = false;
    private boolean softKeyboardVisible = false;

    private long sessionId = -1L;
    private String sessionName;
    private String hostname;
    private int port = 22;
    private String username;
    private String password;
    private int authType = 0;
    private String keyPath;
    private String initialCommand;
    private boolean initialCommandSent = false;

    private final TerminalSession.SessionListener sessionListener = new TerminalSession.SessionListener() {
        @Override
        public void onConnected() {
            updateStatus(getString(R.string.ssh_terminal_connected), STATUS_CONNECTED);
            appendLocalLine(getString(R.string.ssh_terminal_banner_connected, username, hostname, port));
            upsertSessionInfo(true);
            maybeSendInitialCommand();
        }

        @Override
        public void onDisconnected() {
            updateStatus(getString(R.string.ssh_terminal_disconnected), STATUS_DISCONNECTED);
            appendLocalLine(getString(R.string.ssh_terminal_banner_disconnected));
            upsertSessionInfo(false);
        }

        @Override
        public void onDataReceived(String data) {
            if (terminalView != null && !TextUtils.isEmpty(data)) {
                terminalView.append(data);
            }
        }

        @Override
        public void onError(String message) {
            updateStatus(getString(R.string.ssh_terminal_disconnected), STATUS_DISCONNECTED);
            appendLocalLine(getString(R.string.error_prefix, message));
            upsertSessionInfo(false);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ssh_terminal);

        terminalPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        bindViews();
        setupTerminalView();
        setupInputBridge();
        loadBehaviorPrefs();
        loadKeypadMappingFromPrefs();
        setupKeyboardUi();
        setupActions();
        setupSettingsObservers();

        if (!resolveConnectionParams(getIntent())) {
            Toast.makeText(this, getString(R.string.ssh_terminal_missing_host), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        renderHostText();
        connectOrAttach();
    }

    @Override
    protected void onResume() {
        super.onResume();
        softKeyboardVisible = isSoftKeyboardActive();
        updateKeyboardStatusUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!resolveConnectionParams(intent)) {
            Toast.makeText(this, getString(R.string.ssh_terminal_missing_host), Toast.LENGTH_SHORT).show();
            return;
        }
        initialCommandSent = false;
        renderHostText();
        connectOrAttach();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.removeListener(sessionListener);
        }
        if (terminalPrefs != null && prefListener != null) {
            terminalPrefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        if (themeRepository != null) {
            themeRepository.close();
        }
    }

    private void bindViews() {
        hostText = findViewById(R.id.text_terminal_host);
        statusText = findViewById(R.id.text_terminal_status);
        reconnectButton = findViewById(R.id.btn_terminal_reconnect);
        disconnectButton = findViewById(R.id.btn_terminal_disconnect);
        toggleKeypadButton = findViewById(R.id.btn_toggle_keypad);
        toggleSoftKeyboardButton = findViewById(R.id.btn_toggle_soft_keyboard);
        keyboardOptionsButton = findViewById(R.id.btn_keyboard_options);
        keyboardControlBar = findViewById(R.id.keyboard_control_bar);
        quickKeypadPanel = findViewById(R.id.quick_keypad_panel);
        quickKeyRow1 = findViewById(R.id.quick_key_row_1);
        quickKeyRow2 = findViewById(R.id.quick_key_row_2);
        quickKeyRow3 = findViewById(R.id.quick_key_row_3);
        terminalView = findViewById(R.id.terminal_view);
        imeInput = findViewById(R.id.input_terminal_ime);
    }

    private void setupTerminalView() {
        emulator = new TerminalEmulator(80, 24);
        terminalView.attachEmulator(emulator);
        terminalView.setOnResizeListener((cols, rows) -> {
            if (session != null) {
                session.resize(cols, rows);
            }
        });
        terminalView.setOnTerminalGestureListener(new TerminalView.OnTerminalGestureListener() {
            @Override
            public void onDoubleTap() {
                toggleSoftKeyboard();
            }

            @Override
            public void onThreeFingerSwipe(int direction) {
                // no-op for single-session activity
            }

            @Override
            public void onCursorMoveRequest(int column, int row) {
                // no-op
            }

            @Override
            public void onTerminalTap(float x, float y) {
                showSoftKeyboard();
            }

            @Override
            public void onTerminalLongPress(float x, float y) {
                copyTerminalContentToClipboard();
            }

            @Override
            public void onTerminalKeyDown(int keyCode, KeyEvent event) {
                handleHardwareKeyDown(keyCode, event);
            }
        });
        terminalView.setOnClickListener(v -> showSoftKeyboard());
    }

    private void setupInputBridge() {
        imeInput.setOnKeyInputListener(new TerminalInputView.OnKeyInputListener() {
            @Override
            public void onInput(String text) {
                dispatchInput(text);
            }

            @Override
            public void onKeyDown(int keyCode, KeyEvent event) {
                handleHardwareKeyDown(keyCode, event);
            }
        });
    }

    private void setupKeyboardUi() {
        keypadVisible = terminalPrefs.getBoolean(PREF_KEYPAD_VISIBLE, true);
        keyboardLayoutMode = terminalPrefs.getInt(PREF_KEYBOARD_LAYOUT_OPTION, 0);
        rebuildQuickKeypad();
        quickKeypadPanel.setVisibility(keypadVisible ? View.VISIBLE : View.GONE);
        applyKeyboardHeightSetting();
        updateKeyboardStatusUi();
    }

    private void setupActions() {
        reconnectButton.setOnClickListener(v -> reconnectSession());
        disconnectButton.setOnClickListener(v -> disconnectSession());
        toggleKeypadButton.setOnClickListener(v -> toggleKeypadVisibility());
        toggleSoftKeyboardButton.setOnClickListener(v -> toggleSoftKeyboard());
        keyboardOptionsButton.setOnClickListener(v -> showKeyboardOptionsDialog());
    }

    private void setupSettingsObservers() {
        themeRepository = new ThemeRepository(getApplication());
        themeRepository.getTheme().observe(this, this::applyThemeConfig);

        prefListener = (prefs, key) -> {
            if (PREF_ENTER_NEWLINE.equals(key) || PREF_LOCAL_ECHO.equals(key) || PREF_AUTO_SCROLL_OUTPUT.equals(key)) {
                loadBehaviorPrefs();
            } else if (PREF_KEYPAD_VISIBLE.equals(key)) {
                keypadVisible = prefs.getBoolean(PREF_KEYPAD_VISIBLE, true);
                quickKeypadPanel.setVisibility(keypadVisible ? View.VISIBLE : View.GONE);
                updateKeyboardStatusUi();
            } else if (PREF_KEYBOARD_LAYOUT_OPTION.equals(key)) {
                keyboardLayoutMode = prefs.getInt(PREF_KEYBOARD_LAYOUT_OPTION, 0);
                rebuildQuickKeypad();
            } else if (PREF_KEYBOARD_HEIGHT_OPTION.equals(key)) {
                applyKeyboardHeightSetting();
            } else if (PREF_KEYPAD_MAPPING.equals(key)) {
                loadKeypadMappingFromPrefs();
                rebuildQuickKeypad();
            } else if (PREF_SCROLLBACK_LINES.equals(key)) {
                terminalView.setMaxScrollbackLines(prefs.getInt(PREF_SCROLLBACK_LINES, 2000));
            } else if ("terminal_background_scale".equals(key)) {
                applyBackgroundScaleFromPrefs();
            } else if (PREF_BG_URI.equals(key)) {
                applyBackgroundImageFromPrefs();
            }
        };
        terminalPrefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    private void loadBehaviorPrefs() {
        enterNewline = terminalPrefs.getBoolean(PREF_ENTER_NEWLINE, true);
        localEcho = terminalPrefs.getBoolean(PREF_LOCAL_ECHO, false);
        autoScrollOutput = terminalPrefs.getBoolean(PREF_AUTO_SCROLL_OUTPUT, true);
    }

    private void applyThemeConfig(ThemeRepository.ThemeConfig config) {
        if (config == null || terminalView == null) {
            return;
        }
        int[] scheme = buildEffectiveScheme(config);
        terminalView.setColorScheme(scheme);

        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        int fontPx = Math.max(12, Math.round(config.fontSizeSp * scaledDensity));
        terminalView.setFontSize(fontPx);
        terminalView.setLineHeightMultiplier(config.lineHeight <= 0 ? 1.0f : config.lineHeight);
        terminalView.setLetterSpacing(config.letterSpacing);
        terminalView.setTypeface(config.fontFamily == 0 ? Typeface.MONOSPACE : Typeface.SANS_SERIF);
        terminalView.setCursorBlink(config.cursorBlink);
        terminalView.setCursorColor(config.cursorColor);
        terminalView.setSelectionColor(config.selectionColor);
        terminalView.setSearchHighlightColor(config.searchHighlightColor);

        TerminalView.CursorStyle[] styles = TerminalView.CursorStyle.values();
        int styleIndex = config.cursorStyle;
        if (styleIndex < 0 || styleIndex >= styles.length) {
            styleIndex = 0;
        }
        terminalView.setCursorStyle(styles[styleIndex]);
        applyBackgroundScaleFromPrefs();

        terminalView.setBackgroundAlpha(config.backgroundAlpha);
        terminalView.setMaxScrollbackLines(terminalPrefs.getInt(PREF_SCROLLBACK_LINES, 2000));
        applyBackgroundImageFromPrefs();
    }

    private int[] buildEffectiveScheme(ThemeRepository.ThemeConfig config) {
        int[] scheme = new int[16];
        if (config.ansiColors != null && config.ansiColors.length >= 16) {
            System.arraycopy(config.ansiColors, 0, scheme, 0, 16);
        } else {
            System.arraycopy(FALLBACK_SCHEME, 0, scheme, 0, 16);
        }
        scheme[0] = config.backgroundColor;
        scheme[7] = config.foregroundColor;
        scheme[15] = config.foregroundColor;
        return scheme;
    }

    private void applyBackgroundImageFromPrefs() {
        String uriStr = terminalPrefs.getString(PREF_BG_URI, null);
        if (TextUtils.isEmpty(uriStr)) {
            terminalBackground = null;
            terminalView.setBackgroundImage(null);
            return;
        }
        try {
            Uri uri = Uri.parse(uriStr);
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    terminalBackground = BitmapFactory.decodeStream(in);
                    terminalView.setBackgroundImage(terminalBackground);
                }
            }
        } catch (Exception ignored) {
            terminalBackground = null;
            terminalView.setBackgroundImage(null);
        }
    }

    private void applyBackgroundScaleFromPrefs() {
        int scaleIndex = terminalPrefs.getInt("terminal_background_scale", 3);
        TerminalView.BackgroundScale[] scales = TerminalView.BackgroundScale.values();
        if (scaleIndex >= 0 && scaleIndex < scales.length) {
            terminalView.setBackgroundScale(scales[scaleIndex]);
        }
    }

    private void toggleKeypadVisibility() {
        keypadVisible = !keypadVisible;
        quickKeypadPanel.setVisibility(keypadVisible ? View.VISIBLE : View.GONE);
        terminalPrefs.edit().putBoolean(PREF_KEYPAD_VISIBLE, keypadVisible).apply();
        updateKeyboardStatusUi();
    }

    private void updateKeyboardStatusUi() {
        if (keyboardControlBar != null) {
            keyboardControlBar.setVisibility(View.VISIBLE);
        }
        softKeyboardVisible = isSoftKeyboardActive() || softKeyboardVisible;
        if (toggleKeypadButton != null) {
            toggleKeypadButton.setContentDescription(getString(
                keypadVisible ? R.string.terminal_keyboard_toggle_hide : R.string.terminal_keyboard_toggle_show));
            applyControlIconState(toggleKeypadButton, keypadVisible);
        }
        if (toggleSoftKeyboardButton != null) {
            toggleSoftKeyboardButton.setContentDescription(getString(
                softKeyboardVisible ? R.string.terminal_soft_keyboard_toggle_hide : R.string.terminal_soft_keyboard_toggle_show));
            applyControlIconState(toggleSoftKeyboardButton, softKeyboardVisible);
        }
    }

    private void applyControlIconState(MaterialButton button, boolean active) {
        int activeIcon = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF102A43);
        int activeBg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFD7E3FF);
        int inactiveIcon = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurfaceVariant, 0x99000000);
        int inactiveBg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF);
        int outline = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOutline, 0x33000000);

        button.setIconTint(ColorStateList.valueOf(active ? activeIcon : inactiveIcon));
        button.setBackgroundTintList(ColorStateList.valueOf(active ? activeBg : inactiveBg));
        button.setCornerRadius(dpToPx(14));
        button.setStrokeColor(ColorStateList.valueOf(outline));
        button.setStrokeWidth(dpToPx(1));
    }

    private void applyKeyboardHeightSetting() {
        int heightOption = terminalPrefs.getInt(PREF_KEYBOARD_HEIGHT_OPTION, 1);
        ViewGroup.LayoutParams params = quickKeypadPanel.getLayoutParams();
        if (params == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        switch (heightOption) {
            case 0:
                params.height = (int) (density * 124);
                break;
            case 2:
                params.height = (int) (density * 214);
                break;
            case 1:
            default:
                params.height = (int) (density * 168);
                break;
        }
        quickKeypadPanel.setLayoutParams(params);
    }

    private void showKeyboardOptionsDialog() {
        String[] items = new String[] {
            getString(R.string.settings_terminal_keyboard_layout_title),
            getString(R.string.settings_terminal_keyboard_height_title),
            getString(R.string.settings_terminal_keyboard_reset_title)
        };
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.terminal_keyboard_options))
            .setItems(items, (dialog, which) -> {
                if (which == 0) {
                    showKeyboardLayoutDialog();
                } else if (which == 1) {
                    showKeyboardHeightDialog();
                } else {
                    resetKeyboardLayout();
                }
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showKeyboardHeightDialog() {
        String[] options = getResources().getStringArray(R.array.keyboard_height_options);
        int current = terminalPrefs.getInt(PREF_KEYBOARD_HEIGHT_OPTION, 1);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_keyboard_height_title))
            .setSingleChoiceItems(options, current, (dialog, which) -> {
                terminalPrefs.edit().putInt(PREF_KEYBOARD_HEIGHT_OPTION, which).apply();
                applyKeyboardHeightSetting();
                dialog.dismiss();
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showKeyboardLayoutDialog() {
        String[] options = getResources().getStringArray(R.array.keyboard_layout_options);
        int current = terminalPrefs.getInt(PREF_KEYBOARD_LAYOUT_OPTION, 0);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_keyboard_layout_title))
            .setSingleChoiceItems(options, current, (dialog, which) -> {
                terminalPrefs.edit().putInt(PREF_KEYBOARD_LAYOUT_OPTION, which).apply();
                keyboardLayoutMode = which;
                rebuildQuickKeypad();
                dialog.dismiss();
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void resetKeyboardLayout() {
        terminalPrefs.edit()
            .remove(PREF_KEYBOARD_HEIGHT_OPTION)
            .remove(PREF_KEYBOARD_LAYOUT_OPTION)
            .remove(PREF_KEYPAD_MAPPING)
            .putBoolean(PREF_KEYPAD_VISIBLE, true)
            .apply();
        keypadVisible = true;
        keyboardLayoutMode = 0;
        modifierCtrl = false;
        modifierAlt = false;
        modifierShift = false;
        quickKeypadPanel.setVisibility(View.VISIBLE);
        keypadMapping.clear();
        applyKeyboardHeightSetting();
        rebuildQuickKeypad();
        updateKeyboardStatusUi();
        Toast.makeText(this, getString(R.string.settings_terminal_keyboard_reset_done), Toast.LENGTH_SHORT).show();
    }

    private void showEditKeypadMappingDialog(String label, String defaultValue) {
        EditText edit = new EditText(this);
        edit.setHint(getString(R.string.settings_terminal_keypad_hint));
        edit.setText(keypadMapping.containsKey(label) ? keypadMapping.get(label) : defaultValue);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_keypad_edit, label))
            .setView(edit)
            .setPositiveButton(R.string.action_save, (d, w) -> {
                String value = edit.getText() == null ? "" : edit.getText().toString();
                if (TextUtils.isEmpty(value)) {
                    keypadMapping.remove(label);
                } else {
                    keypadMapping.put(label, value);
                }
                persistKeypadMapping();
                rebuildQuickKeypad();
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void loadKeypadMappingFromPrefs() {
        keypadMapping.clear();
        String json = terminalPrefs.getString(PREF_KEYPAD_MAPPING, null);
        if (TextUtils.isEmpty(json)) {
            return;
        }
        try {
            JSONObject obj = new JSONObject(json);
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String k = it.next();
                keypadMapping.put(k, obj.optString(k));
            }
        } catch (Exception ignored) {
            keypadMapping.clear();
        }
    }

    private void persistKeypadMapping() {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, String> e : keypadMapping.entrySet()) {
            try {
                obj.put(e.getKey(), e.getValue());
            } catch (Exception ignored) {
            }
        }
        terminalPrefs.edit().putString(PREF_KEYPAD_MAPPING, obj.toString()).apply();
    }

    private void rebuildQuickKeypad() {
        quickKeyButtons.clear();
        quickKeyRow1.removeAllViews();
        quickKeyRow2.removeAllViews();
        quickKeyRow3.removeAllViews();

        List<QuickKeySpec> row1 = new ArrayList<>();
        List<QuickKeySpec> row2 = new ArrayList<>();
        List<QuickKeySpec> row3 = new ArrayList<>();

        if (keyboardLayoutMode == 1) {
            // Programming
            row1.add(new QuickKeySpec("Esc", "ESC", ESC, QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("Alt", "ALT", "ALT", QuickKeyAction.MODIFIER));
            row1.add(new QuickKeySpec("{", "{", "{", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("}", "}", "}", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("[", "[", "[", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("]", "]", "]", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("IME", "IME", "IME", QuickKeyAction.TOGGLE_IME));

            row2.add(new QuickKeySpec("Tab", "TAB", "\t", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("Ctrl", "CTRL", "CTRL", QuickKeyAction.MODIFIER));
            row2.add(new QuickKeySpec("(", "(", "(", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec(")", ")", ")", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("|", "|", "|", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("\\", "\\", "\\", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("Paste", "PASTE", "", QuickKeyAction.PASTE));

            row3.add(new QuickKeySpec("Shift", "SHIFT", "SHIFT", QuickKeyAction.MODIFIER));
            row3.add(new QuickKeySpec("_", "_", "_", QuickKeyAction.SEND));
            row3.add(new QuickKeySpec("-", "-", "-", QuickKeyAction.SEND));
            row3.add(new QuickKeySpec(";", ";", ";", QuickKeyAction.SEND));
            row3.add(new QuickKeySpec("=", "=", "=", QuickKeyAction.SEND));
            row3.add(new QuickKeySpec("KBD", "KBD", "", QuickKeyAction.TOGGLE_PANEL));
        } else if (keyboardLayoutMode == 2) {
            // Server admin
            row1.add(new QuickKeySpec("Esc", "ESC", ESC, QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("Alt", "ALT", "ALT", QuickKeyAction.MODIFIER));
            row1.add(new QuickKeySpec("Home", "HOME", ESC + "[H", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("↑", "↑", ESC + "[A", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("End", "END", ESC + "[F", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("PgUp", "PGUP", ESC + "[5~", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("PgDn", "PGDN", ESC + "[6~", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("IME", "IME", "IME", QuickKeyAction.TOGGLE_IME));

            row2.add(new QuickKeySpec("Tab", "TAB", "\t", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("Ctrl", "CTRL", "CTRL", QuickKeyAction.MODIFIER));
            row2.add(new QuickKeySpec("←", "←", ESC + "[D", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("↓", "↓", ESC + "[B", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("→", "→", ESC + "[C", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("Ctrl+C", "CTRL+C", "\u0003", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("Ctrl+Z", "CTRL+Z", "\u001a", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("Paste", "PASTE", "", QuickKeyAction.PASTE));

            row3.add(new QuickKeySpec("Shift", "SHIFT", "SHIFT", QuickKeyAction.MODIFIER));
            row3.add(new QuickKeySpec("Ins", "INS", ESC + "[2~", QuickKeyAction.SEND));
            row3.add(new QuickKeySpec("Del", "DEL", ESC + "[3~", QuickKeyAction.SEND));
            row3.add(new QuickKeySpec("Copy", "COPY", "", QuickKeyAction.COPY));
            row3.add(new QuickKeySpec("KBD", "KBD", "", QuickKeyAction.TOGGLE_PANEL));
        } else {
            // Standard (screenshot-like)
            row1.add(new QuickKeySpec("Esc", "ESC", ESC, QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("Alt", "ALT", "ALT", QuickKeyAction.MODIFIER));
            row1.add(new QuickKeySpec("Home", "HOME", ESC + "[H", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("↑", "↑", ESC + "[A", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("End", "END", ESC + "[F", QuickKeyAction.SEND));
            row1.add(new QuickKeySpec("Copy", "COPY", "", QuickKeyAction.COPY));
            row1.add(new QuickKeySpec("IME", "IME", "IME", QuickKeyAction.TOGGLE_IME));

            row2.add(new QuickKeySpec("Tab", "TAB", "\t", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("Ctrl", "CTRL", "CTRL", QuickKeyAction.MODIFIER));
            row2.add(new QuickKeySpec("←", "←", ESC + "[D", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("↓", "↓", ESC + "[B", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("→", "→", ESC + "[C", QuickKeyAction.SEND));
            row2.add(new QuickKeySpec("Paste", "PASTE", "", QuickKeyAction.PASTE));
            row2.add(new QuickKeySpec("KBD", "KBD", "", QuickKeyAction.TOGGLE_PANEL));

            row3.add(new QuickKeySpec("Shift", "SHIFT", "SHIFT", QuickKeyAction.MODIFIER));
        }

        addQuickKeyRow(quickKeyRow1, row1);
        addQuickKeyRow(quickKeyRow2, row2);
        addQuickKeyRow(quickKeyRow3, row3);
        updateModifierIndicators();
    }

    private void addQuickKeyRow(LinearLayout rowView, List<QuickKeySpec> specs) {
        if (specs == null || specs.isEmpty()) {
            rowView.setVisibility(View.GONE);
            return;
        }
        rowView.setVisibility(View.VISIBLE);
        rowView.removeAllViews();

        boolean singleKeyRow = specs.size() == 1;
        for (int i = 0; i < specs.size(); i++) {
            QuickKeySpec spec = specs.get(i);
            MaterialButton btn = createQuickKeyButton(spec, singleKeyRow);
            rowView.addView(btn);
            quickKeyButtons.put(spec.mapKey, btn);
        }
        if (singleKeyRow) {
            View spacer = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            spacer.setLayoutParams(lp);
            rowView.addView(spacer);
        }
    }

    private MaterialButton createQuickKeyButton(QuickKeySpec spec, boolean compact) {
        MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setAllCaps(false);
        btn.setTextSize(12.5f);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);

        int padH = dpToPx(8);
        int padV = dpToPx(7);
        btn.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams lp = compact
            ? new LinearLayout.LayoutParams(dpToPx(88), ViewGroup.LayoutParams.WRAP_CONTENT)
            : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int margin = dpToPx(3);
        lp.setMargins(margin, margin, margin, margin);
        btn.setLayoutParams(lp);

        int iconRes = resolveQuickKeyIcon(spec);
        if (iconRes != 0) {
            btn.setText("");
            btn.setIconResource(iconRes);
            btn.setIconSize(dpToPx(16));
            btn.setIconPadding(0);
            btn.setContentDescription(spec.label);
        } else {
            btn.setText(spec.label);
            btn.setContentDescription(spec.label);
        }

        styleQuickKeyButton(btn, false);

        btn.setOnClickListener(v -> onQuickKeyPressed(spec));
        if (spec.action == QuickKeyAction.SEND) {
            btn.setOnLongClickListener(v -> {
                showEditKeypadMappingDialog(spec.mapKey, spec.defaultValue);
                return true;
            });
        }
        return btn;
    }

    private int resolveQuickKeyIcon(QuickKeySpec spec) {
        if (spec.action == QuickKeyAction.COPY) {
            return R.drawable.ic_action_content_copy;
        }
        if (spec.action == QuickKeyAction.PASTE) {
            return R.drawable.ic_action_content_paste;
        }
        if (spec.action == QuickKeyAction.TOGGLE_IME) {
            return R.drawable.ic_action_keyboard;
        }
        if (spec.action == QuickKeyAction.TOGGLE_PANEL) {
            return R.drawable.ic_action_terminal;
        }
        return 0;
    }

    private void styleQuickKeyButton(MaterialButton button, boolean active) {
        int fg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF333333);
        int outline = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOutline, 0x33000000);
        int inactiveBg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorSurface, 0xFFF6F7F9);
        int activeBg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFD7E3FF);
        int activeFg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimaryContainer, fg);

        button.setCornerRadius(dpToPx(10));
        button.setStrokeWidth(dpToPx(1));
        button.setStrokeColor(ColorStateList.valueOf(outline));
        button.setBackgroundTintList(ColorStateList.valueOf(active ? activeBg : inactiveBg));
        ColorStateList textOrIconColor = ColorStateList.valueOf(active ? activeFg : fg);
        button.setTextColor(textOrIconColor);
        button.setIconTint(textOrIconColor);
    }

    private void onQuickKeyPressed(QuickKeySpec spec) {
        if (spec.action == QuickKeyAction.MODIFIER) {
            toggleModifier(spec.mapKey);
            return;
        }
        if (spec.action == QuickKeyAction.COPY) {
            copyTerminalContentToClipboard();
            resetModifiers();
            return;
        }
        if (spec.action == QuickKeyAction.PASTE) {
            pasteFromClipboard();
            resetModifiers();
            return;
        }
        if (spec.action == QuickKeyAction.TOGGLE_IME) {
            toggleSoftKeyboard();
            resetModifiers();
            return;
        }
        if (spec.action == QuickKeyAction.TOGGLE_PANEL) {
            toggleKeypadVisibility();
            resetModifiers();
            return;
        }

        String raw = keypadMapping.containsKey(spec.mapKey) ? keypadMapping.get(spec.mapKey) : spec.defaultValue;
        if (TextUtils.isEmpty(raw)) {
            resetModifiers();
            return;
        }

        String send = applyModifiers(raw);
        dispatchInput(send);
        resetModifiers();
    }

    private void toggleModifier(String key) {
        if ("CTRL".equals(key)) {
            modifierCtrl = !modifierCtrl;
        } else if ("ALT".equals(key)) {
            modifierAlt = !modifierAlt;
        } else if ("SHIFT".equals(key)) {
            modifierShift = !modifierShift;
        }
        updateModifierIndicators();
    }

    private void resetModifiers() {
        modifierCtrl = false;
        modifierAlt = false;
        modifierShift = false;
        updateModifierIndicators();
    }

    private void updateModifierIndicators() {
        styleModifier("CTRL", modifierCtrl);
        styleModifier("ALT", modifierAlt);
        styleModifier("SHIFT", modifierShift);
    }

    private void styleModifier(String key, boolean active) {
        MaterialButton btn = quickKeyButtons.get(key);
        if (btn != null) {
            styleQuickKeyButton(btn, active);
        }
    }

    private String applyModifiers(String value) {
        if (TextUtils.isEmpty(value)) {
            return value;
        }
        if (!modifierCtrl && !modifierAlt && !modifierShift) {
            return value;
        }

        // Shift+Tab
        if ("\t".equals(value) && modifierShift && !modifierCtrl && !modifierAlt) {
            return ESC + "[Z";
        }

        if (value.startsWith(ESC + "[") && value.length() >= 3) {
            int mod = buildXtermModifierCode();
            if (mod > 1) {
                char last = value.charAt(value.length() - 1);
                if (last == 'A' || last == 'B' || last == 'C' || last == 'D' || last == 'H' || last == 'F') {
                    return ESC + "[1;" + mod + last;
                }
                if (last == '~') {
                    String inner = value.substring(2, value.length() - 1);
                    return ESC + "[" + inner + ";" + mod + "~";
                }
            }
            return value;
        }

        if (value.length() == 1) {
            char c = value.charAt(0);
            if (modifierShift && c >= 'a' && c <= 'z') {
                c = Character.toUpperCase(c);
            }
            String base = String.valueOf(c);
            if (modifierCtrl) {
                char upper = Character.toUpperCase(c);
                if (upper >= 'A' && upper <= 'Z') {
                    base = String.valueOf((char) (upper & 0x1F));
                }
            }
            if (modifierAlt) {
                return ESC + base;
            }
            return base;
        }

        if (modifierAlt && !value.startsWith(ESC)) {
            return ESC + value;
        }
        return value;
    }

    private int buildXtermModifierCode() {
        int mod = 1;
        if (modifierShift) {
            mod += 1;
        }
        if (modifierAlt) {
            mod += 2;
        }
        if (modifierCtrl) {
            mod += 4;
        }
        return mod;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showSoftKeyboard() {
        if (imeInput == null) {
            return;
        }
        imeInput.requestFocus();
        imeInput.post(() -> {
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(imeInput, InputMethodManager.SHOW_IMPLICIT);
            }
            softKeyboardVisible = true;
            updateKeyboardStatusUi();
        });
    }

    private void hideSoftKeyboard() {
        IBinder token = null;
        View focus = getCurrentFocus();
        if (focus != null) {
            token = focus.getWindowToken();
        }
        if (token == null && imeInput != null) {
            token = imeInput.getWindowToken();
        }
        if (inputMethodManager != null && token != null) {
            inputMethodManager.hideSoftInputFromWindow(token, 0);
        }
        softKeyboardVisible = false;
        updateKeyboardStatusUi();
    }

    private void toggleSoftKeyboard() {
        if (isSoftKeyboardActive() || softKeyboardVisible) {
            hideSoftKeyboard();
        } else {
            showSoftKeyboard();
        }
    }

    private boolean isSoftKeyboardActive() {
        return inputMethodManager != null && imeInput != null && inputMethodManager.isActive(imeInput);
    }

    private void copyTerminalContentToClipboard() {
        String content = terminalView == null ? null : terminalView.getTerminalContent();
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, getString(R.string.terminal_content_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", content));
        Toast.makeText(this, getString(R.string.action_copied), Toast.LENGTH_SHORT).show();
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, getString(R.string.terminal_clipboard_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData data = clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) {
            Toast.makeText(this, getString(R.string.terminal_clipboard_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = data.getItemAt(0).coerceToText(this);
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, getString(R.string.terminal_clipboard_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        imeInput.pasteText(text.toString());
        Toast.makeText(this, getString(R.string.msg_paste_success), Toast.LENGTH_SHORT).show();
    }

    private void handleHardwareKeyDown(int keyCode, KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }

        String seq = getAnsiSequence(keyCode, event);
        if (seq != null) {
            dispatchInput(seq);
            return;
        }

        int unicode = event.getUnicodeChar();
        if (unicode > 0 && !event.isCtrlPressed()) {
            dispatchInput(String.valueOf((char) unicode));
        }
    }

    private String getAnsiSequence(int keyCode, KeyEvent event) {
        String numpadSeq = getNumpadSequence(event);
        if (numpadSeq != null) {
            return numpadSeq;
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            return "\r";
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            return "\u007f";
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            return ESC;
        }
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            return event.isShiftPressed() ? ESC + "[Z" : "\t";
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return getModifiedArrowSequence("A", event);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return getModifiedArrowSequence("B", event);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return getModifiedArrowSequence("C", event);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return getModifiedArrowSequence("D", event);
        }

        if (keyCode == KeyEvent.KEYCODE_MOVE_HOME) {
            return event.isCtrlPressed() ? ESC + "[1;5H" : ESC + "[H";
        }
        if (keyCode == KeyEvent.KEYCODE_MOVE_END) {
            return event.isCtrlPressed() ? ESC + "[1;5F" : ESC + "[F";
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            return event.isCtrlPressed() ? ESC + "[5;5~" : ESC + "[5~";
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            return event.isCtrlPressed() ? ESC + "[6;5~" : ESC + "[6~";
        }
        if (keyCode == KeyEvent.KEYCODE_INSERT) {
            return event.isCtrlPressed() ? ESC + "[2;5~" : ESC + "[2~";
        }
        if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            return event.isCtrlPressed() ? ESC + "[3;5~" : ESC + "[3~";
        }

        String fkeySeq = getFunctionKeySequence(keyCode, event);
        if (fkeySeq != null) {
            return fkeySeq;
        }

        if (event.isCtrlPressed()) {
            int unicode = event.getUnicodeChar(event.getMetaState() & ~KeyEvent.META_CTRL_MASK);
            if (unicode >= 'a' && unicode <= 'z') {
                return String.valueOf((char) (unicode - 'a' + 1));
            }
            if (unicode >= 'A' && unicode <= 'Z') {
                return String.valueOf((char) (unicode - 'A' + 1));
            }
        }

        if (event.isAltPressed()) {
            int unicode = event.getUnicodeChar();
            if (unicode >= 32 && unicode <= 126) {
                return ESC + String.valueOf((char) unicode);
            }
        }

        return null;
    }

    private String getModifiedArrowSequence(String baseSeq, KeyEvent event) {
        if (event.isCtrlPressed() && event.isAltPressed() && event.isShiftPressed()) {
            return ESC + "[1;8" + baseSeq;
        }
        if (event.isCtrlPressed() && event.isAltPressed()) {
            return ESC + "[1;7" + baseSeq;
        }
        if (event.isCtrlPressed() && event.isShiftPressed()) {
            return ESC + "[1;6" + baseSeq;
        }
        if (event.isCtrlPressed()) {
            return ESC + "[1;5" + baseSeq;
        }
        if (event.isAltPressed() && event.isShiftPressed()) {
            return ESC + "[1;4" + baseSeq;
        }
        if (event.isAltPressed()) {
            return ESC + "[1;3" + baseSeq;
        }
        if (event.isShiftPressed()) {
            return ESC + "[1;2" + baseSeq;
        }
        return ESC + "[" + baseSeq;
    }

    private String getNumpadSequence(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return String.valueOf(keyCode - KeyEvent.KEYCODE_NUMPAD_0);
        }

        switch (keyCode) {
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
            case KeyEvent.KEYCODE_F1:
                return ESC + "OP";
            case KeyEvent.KEYCODE_F2:
                return ESC + "OQ";
            case KeyEvent.KEYCODE_F3:
                return ESC + "OR";
            case KeyEvent.KEYCODE_F4:
                return ESC + "OS";
            case KeyEvent.KEYCODE_F5:
                return ESC + "[15" + modifier + "~";
            case KeyEvent.KEYCODE_F6:
                return ESC + "[17" + modifier + "~";
            case KeyEvent.KEYCODE_F7:
                return ESC + "[18" + modifier + "~";
            case KeyEvent.KEYCODE_F8:
                return ESC + "[19" + modifier + "~";
            case KeyEvent.KEYCODE_F9:
                return ESC + "[20" + modifier + "~";
            case KeyEvent.KEYCODE_F10:
                return ESC + "[21" + modifier + "~";
            case KeyEvent.KEYCODE_F11:
                return ESC + "[23" + modifier + "~";
            case KeyEvent.KEYCODE_F12:
                return ESC + "[24" + modifier + "~";
            default:
                return null;
        }
    }

    private boolean resolveConnectionParams(Intent intent) {
        if (intent == null) {
            intent = new Intent();
        }

        Long hintedId = parseSessionIdHint(intent);
        SessionInfo hintedInfo = hintedId == null ? null : findSessionInfoById(hintedId);

        String intentHost = firstNonEmpty(intent.getStringExtra("hostname"), intent.getStringExtra("host_name"));
        String intentUser = firstNonEmpty(intent.getStringExtra("username"), intent.getStringExtra("host_user"));
        int intentPort = parsePort(intent);
        String intentPassword = firstNonEmpty(intent.getStringExtra("password"), intent.getStringExtra("host_pass"));
        int intentAuthType = parseAuthType(intent);
        String intentKeyPath = firstNonEmpty(intent.getStringExtra("key_path"), intent.getStringExtra("host_key"));
        initialCommand = intent.getStringExtra("initial_command");

        String prefHost = terminalPrefs.getString("current_host_hostname", null);
        String prefUser = terminalPrefs.getString("current_host_username", null);
        int prefPort = terminalPrefs.getInt("current_host_port", -1);

        hostname = firstNonEmpty(intentHost, hintedInfo == null ? null : hintedInfo.hostname, prefHost);
        username = firstNonEmpty(intentUser, hintedInfo == null ? null : hintedInfo.username, prefUser);
        port = firstPositive(intentPort, hintedInfo == null ? -1 : hintedInfo.port, prefPort, 22);

        SessionInfo match = findSessionInfo(hostname, port, username);
        password = firstNonEmpty(intentPassword, hintedInfo == null ? null : hintedInfo.password, match == null ? null : match.password);
        authType = firstPositive(intentAuthType, hintedInfo == null ? -1 : hintedInfo.authType, match == null ? -1 : match.authType, 0);
        keyPath = firstNonEmpty(intentKeyPath, hintedInfo == null ? null : hintedInfo.keyPath, match == null ? null : match.keyPath);

        if (match != null) {
            sessionId = match.id;
        } else if (hintedInfo != null && hintedInfo.id > 0) {
            sessionId = hintedInfo.id;
        } else if (intent.hasExtra("focus_container_id") && hintedId != null && hintedId > 0) {
            sessionId = hintedId;
        } else {
            sessionId = System.currentTimeMillis();
        }

        String alias = intent.getStringExtra("host_alias");
        sessionName = !TextUtils.isEmpty(alias) ? alias : username + "@" + hostname;
        return !TextUtils.isEmpty(hostname) && !TextUtils.isEmpty(username) && port > 0;
    }

    private void renderHostText() {
        hostText.setText(getString(R.string.session_host_format, username, hostname, port));
    }

    private void connectOrAttach() {
        TerminalSession preferred = SessionManager.getInstance().getTerminalSession(sessionId);
        if (preferred != null && preferred.isConnected()) {
            attachSession(preferred, true);
            return;
        }
        TerminalSession reusable = SessionManager.getInstance().findConnectedSession(hostname, port, username);
        if (reusable != null) {
            attachSession(reusable, true);
            return;
        }
        connectNewSession();
    }

    private void attachSession(TerminalSession target, boolean reused) {
        if (target == null) {
            return;
        }
        if (session != null && session != target) {
            session.removeListener(sessionListener);
        }
        session = target;
        session.addListener(sessionListener);
        updateStatus(getString(R.string.ssh_terminal_connected), STATUS_CONNECTED);
        if (reused) {
            appendLocalLine(getString(R.string.ssh_terminal_banner_reusing_session));
        }
        upsertSessionInfo(true);
        maybeSendInitialCommand();
    }

    private void connectNewSession() {
        if (session != null) {
            session.removeListener(sessionListener);
        }
        session = new TerminalSession();
        session.setHostKeyVerifier(createHostKeyVerifier());
        session.addListener(sessionListener);
        updateStatus(getString(R.string.ssh_terminal_connecting), STATUS_CONNECTING);
        appendLocalLine(getString(R.string.ssh_terminal_banner_connecting, username, hostname, port));
        upsertSessionInfo(false);
        session.connect(hostname, port, username, password, authType, keyPath);
    }

    private void reconnectSession() {
        initialCommandSent = false;
        if (session != null) {
            session.removeListener(sessionListener);
            session.disconnect();
        }
        connectNewSession();
    }

    private void disconnectSession() {
        if (session != null) {
            session.disconnect();
        }
    }

    private void dispatchInput(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        String send = normalizeLineEnding(text);
        if (localEcho) {
            terminalView.append(send);
        }
        if (autoScrollOutput) {
            terminalView.scrollToBottom();
        }
        if (session == null || !session.isConnected()) {
            Toast.makeText(this, getString(R.string.ssh_terminal_not_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        session.write(send);
    }

    private String normalizeLineEnding(String text) {
        if (text == null) {
            return "";
        }
        if (enterNewline) {
            return text.replace("\r", "\n");
        }
        return text.replace("\n", "\r");
    }

    private void maybeSendInitialCommand() {
        if (initialCommandSent || TextUtils.isEmpty(initialCommand) || session == null || !session.isConnected()) {
            return;
        }
        initialCommandSent = true;
        dispatchInput(initialCommand + "\r");
    }

    private void appendLocalLine(String line) {
        if (terminalView == null || TextUtils.isEmpty(line)) {
            return;
        }
        terminalView.append(line + "\r\n");
    }

    private void updateStatus(String status, int color) {
        statusText.setText(status);
        statusText.setTextColor(color);
        boolean connected = session != null && session.isConnected();
        disconnectButton.setEnabled(connected);
        reconnectButton.setEnabled(!TextUtils.isEmpty(hostname) && !TextUtils.isEmpty(username));
    }

    private void upsertSessionInfo(boolean connected) {
        if (sessionId <= 0 || TextUtils.isEmpty(hostname) || TextUtils.isEmpty(username)) {
            return;
        }
        SessionInfo info = new SessionInfo(
            sessionId,
            TextUtils.isEmpty(sessionName) ? (username + "@" + hostname) : sessionName,
            hostname,
            port,
            username,
            password,
            authType,
            keyPath,
            connected
        );
        SessionManager.getInstance().upsertSession(info, session);
    }

    private HostKeyVerifier createHostKeyVerifier() {
        return (host, verifyPort, fingerprint, status) -> {
            if (status == 0) {
                return true;
            }
            final AtomicBoolean approved = new AtomicBoolean(false);
            final CountDownLatch latch = new CountDownLatch(1);
            runOnUiThread(() -> {
                String title;
                String message;
                if (status == 2) {
                    title = "SSH Host Key Verification";
                    message = "The authenticity of host '" + host + "' can't be established.\n"
                        + "Fingerprint (SHA256): " + fingerprint + "\n\n"
                        + "Are you sure you want to continue connecting?";
                } else if (status == 1) {
                    title = "SSH Host Key Mismatch";
                    message = "REMOTE HOST IDENTIFICATION HAS CHANGED.\n\n"
                        + "Fingerprint (SHA256): " + fingerprint + "\n\n"
                        + "Continue anyway?";
                } else {
                    latch.countDown();
                    return;
                }

                new AlertDialog.Builder(SshTerminalActivity.this)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.action_connect, (d, w) -> {
                        approved.set(true);
                        latch.countDown();
                    })
                    .setNegativeButton(R.string.action_cancel, (d, w) -> latch.countDown())
                    .show();
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return approved.get();
        };
    }

    private Long parseSessionIdHint(Intent intent) {
        long focusId = intent.getLongExtra("focus_container_id", -1L);
        if (focusId > 0) {
            return focusId;
        }
        long hostId = intent.getLongExtra("host_id", -1L);
        return hostId > 0 ? hostId : null;
    }

    private int parsePort(Intent intent) {
        if (intent.hasExtra("port")) {
            return intent.getIntExtra("port", 22);
        }
        if (intent.hasExtra("host_port")) {
            return intent.getIntExtra("host_port", 22);
        }
        return -1;
    }

    private int parseAuthType(Intent intent) {
        if (intent.hasExtra("auth_type")) {
            return intent.getIntExtra("auth_type", 0);
        }
        if (intent.hasExtra("host_auth")) {
            return intent.getIntExtra("host_auth", 0);
        }
        return -1;
    }

    private SessionInfo findSessionInfoById(long id) {
        List<SessionInfo> infos = SessionManager.getInstance().getSessions();
        for (SessionInfo info : infos) {
            if (info != null && info.id == id) {
                return info;
            }
        }
        return null;
    }

    private SessionInfo findSessionInfo(String host, int p, String user) {
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(user) || p <= 0) {
            return null;
        }
        List<SessionInfo> infos = SessionManager.getInstance().getSessions();
        for (SessionInfo info : infos) {
            if (info == null) {
                continue;
            }
            if (p == info.port && TextUtils.equals(host, info.hostname) && TextUtils.equals(user, info.username)) {
                return info;
            }
        }
        return null;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) {
                return v;
            }
        }
        return null;
    }

    private static int firstPositive(int... values) {
        if (values == null || values.length == 0) {
            return -1;
        }
        for (int v : values) {
            if (v >= 0) {
                return v;
            }
        }
        return values[values.length - 1];
    }
}
