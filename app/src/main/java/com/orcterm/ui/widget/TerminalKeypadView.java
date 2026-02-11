package com.orcterm.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.orcterm.R;
import com.google.android.material.button.MaterialButton;
import androidx.core.content.ContextCompat;

public class TerminalKeypadView extends HorizontalScrollView {

    public interface OnKeypadClickListener {
        void onKeyClick(String key, String value);
    }

    public interface OnKeypadLongClickListener {
        void onKeyLongClick(String key, String value);
    }
    
    public interface OnLayoutChangeListener {
        void onLayoutChanged(int mode);
    }

    private OnKeypadClickListener listener;
    private OnKeypadLongClickListener longClickListener;
    private OnLayoutChangeListener layoutChangeListener;
    private LinearLayout rootLayout;
    private java.util.Map<String, String> customMapping = new java.util.HashMap<>();

    // 修饰键状态
    private boolean isCtrlPressed = false;
    private boolean isAltPressed = false;
    private boolean isMetaPressed = false;
    private MaterialButton btnCtrl;
    private MaterialButton btnAlt;
    private MaterialButton btnMeta;
    private int keyBgColor;
    private int keyBgActiveColor;
    private int keyTextColor;
    private int keyTextActiveColor;
    private int keyHeightPx;
    private int keyPaddingPx;
    private int keyMarginPx;
    private int keyCornerRadiusPx;
    private int basePadding;
    private int layoutMode = 0; // 0=standard, 1=programming, 2=server

    public TerminalKeypadView(Context context) {
        super(context);
        init(context);
    }

    public TerminalKeypadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        keyBgColor = ContextCompat.getColor(context, R.color.terminal_key_bg);
        keyBgActiveColor = ContextCompat.getColor(context, R.color.terminal_key_bg_active);
        keyTextColor = ContextCompat.getColor(context, R.color.terminal_key_text);
        keyTextActiveColor = ContextCompat.getColor(context, R.color.terminal_key_text_active);
        keyHeightPx = dpToPx(28);
        keyPaddingPx = dpToPx(6);
        keyMarginPx = dpToPx(2);
        keyCornerRadiusPx = dpToPx(10);
        basePadding = dpToPx(4);
        rootLayout.setPadding(basePadding, basePadding, basePadding, basePadding);
        buildLayout(context);
        
        this.addView(rootLayout);
        this.setFillViewport(true);
        this.setBackgroundColor(ContextCompat.getColor(context, R.color.terminal_termius_surface_variant));
    }

    private MaterialButton addKey(Context context, LinearLayout parent, String label, String value) {
        return addKey(context, parent, label, value, 0f, 0);
    }

    private MaterialButton addKey(Context context, LinearLayout parent, String label, String value, float weight, int minWidthDp) {
        MaterialButton btn = new MaterialButton(context);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTextColor(keyTextColor);
        btn.setCornerRadius(keyCornerRadiusPx);
        btn.setStrokeWidth(0);
        btn.setBackgroundTintList(ColorStateList.valueOf(keyBgColor));
        btn.setPadding(keyPaddingPx, 0, keyPaddingPx, 0);
        LinearLayout.LayoutParams params = weight > 0f
                ? new LinearLayout.LayoutParams(0, keyHeightPx, weight)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, keyHeightPx);
        params.setMargins(keyMarginPx, 0, keyMarginPx, 0);
        btn.setLayoutParams(params);
        if (minWidthDp > 0) {
            btn.setMinWidth(dpToPx(minWidthDp));
        }

        btn.setOnClickListener(v -> {
            handleKeyPress(label, value);
        });
        btn.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                String mapped = customMapping.get(label);
                longClickListener.onKeyLongClick(label, mapped != null ? mapped : value);
                return true;
            }
            return false;
        });

        parent.addView(btn);
        return btn;
    }

    private void buildLayout(Context context) {
        rootLayout.removeAllViews();
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row3 = new LinearLayout(context);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row4 = new LinearLayout(context);
        row4.setOrientation(LinearLayout.HORIZONTAL);

        row1.setPadding(0, 0, 0, dpToPx(4));
        row2.setPadding(0, 0, 0, dpToPx(4));
        row3.setPadding(0, 0, 0, dpToPx(4));

        // Common head row
        addKey(context, row1, "KBD", "KBD", 0f, 56);
        addKey(context, row1, "LAY", "LAY", 0f, 56);
        addKey(context, row1, "ESC", "\u001b");
        addKey(context, row1, "TAB", "\t");
        addKey(context, row1, "⌫", "\u007f");
        btnCtrl = addKey(context, row1, "CTRL", "CTRL");
        btnAlt = addKey(context, row1, "ALT", "ALT");
        btnMeta = addKey(context, row1, "META", "META");

        if (layoutMode == 1) {
            // Programming layout
            addKey(context, row2, "(", "(");
            addKey(context, row2, ")", ")");
            addKey(context, row2, "[", "[");
            addKey(context, row2, "]", "]");
            addKey(context, row2, "{", "{");
            addKey(context, row2, "}", "}");
            addKey(context, row2, "<", "<");
            addKey(context, row2, ">", ">");
            addKey(context, row2, "=", "=");
            addKey(context, row2, "==", "==");
            addKey(context, row2, "!=", "!=");

            addKey(context, row3, "&", "&");
            addKey(context, row3, "&&", "&&");
            addKey(context, row3, "|", "|");
            addKey(context, row3, "||", "||");
            addKey(context, row3, "->", "->");
            addKey(context, row3, "=>", "=>");
            addKey(context, row3, "::", "::");
            addKey(context, row3, "//", "//");
            addKey(context, row3, "/*", "/*");
            addKey(context, row3, "*/", "*/");
            addKey(context, row3, "-", "-");
            addKey(context, row3, "_", "_");
            addKey(context, row3, ".", ".");
            addKey(context, row3, ",", ",");
            addKey(context, row3, ":", ":");
            addKey(context, row3, ";", ";");
            addKey(context, row3, "?", "?");

            addKey(context, row4, "CTRL+C", "\u0003");
            addKey(context, row4, "CTRL+V", "\u0016");
            addKey(context, row4, "CTRL+Z", "\u001a");
            addKey(context, row4, "CTRL+L", "\u000c");
            addKey(context, row4, "CTRL+U", "\u0015");
            addKey(context, row4, "CTRL+W", "\u0017");
            addKey(context, row4, "SPACE", " ", 2f, 72);
        } else if (layoutMode == 2) {
            // Server layout
            addKey(context, row2, "INS", "\u001b[2~");
            addKey(context, row2, "HOME", "\u001b[1~");
            addKey(context, row2, "END", "\u001b[4~");
            addKey(context, row2, "PGUP", "\u001b[5~");
            addKey(context, row2, "PGDN", "\u001b[6~");
            addKey(context, row2, "↑", "\u001b[A");
            addKey(context, row2, "↓", "\u001b[B");
            addKey(context, row2, "←", "\u001b[D");
            addKey(context, row2, "→", "\u001b[C");

            addKey(context, row3, "|", "|");
            addKey(context, row3, "&", "&");
            addKey(context, row3, ";", ";");
            addKey(context, row3, ":", ":");
            addKey(context, row3, "/", "/");
            addKey(context, row3, "\\", "\\");
            addKey(context, row3, "-", "-");
            addKey(context, row3, "_", "_");
            addKey(context, row3, "~", "~");
            addKey(context, row3, "`", "`");

            addKey(context, row4, "CTRL+C", "\u0003");
            addKey(context, row4, "CTRL+D", "\u0004");
            addKey(context, row4, "CTRL+Z", "\u001a");
            addKey(context, row4, "CTRL+L", "\u000c");
            addKey(context, row4, "CTRL+R", "\u0012");
            addKey(context, row4, "CTRL+W", "\u0017");
            addKey(context, row4, "CTRL+B", "\u0002");
            addKey(context, row4, "CTRL+P", "\u0010");
            addKey(context, row4, "CTRL+N", "\u000e");
            addKey(context, row4, "CTRL+E", "\u0005");
            addKey(context, row4, "CTRL+K", "\u000b");
            addKey(context, row4, "SPACE", " ", 2f, 72);
        } else {
            // Standard layout
            addKey(context, row2, "HOME", "\u001b[1~");
            addKey(context, row2, "END", "\u001b[4~");
            addKey(context, row2, "PGUP", "\u001b[5~");
            addKey(context, row2, "PGDN", "\u001b[6~");
            addKey(context, row2, "↑", "\u001b[A");
            addKey(context, row2, "↓", "\u001b[B");
            addKey(context, row2, "←", "\u001b[D");
            addKey(context, row2, "→", "\u001b[C");

            addKey(context, row3, "|", "|");
            addKey(context, row3, "&", "&");
            addKey(context, row3, "~", "~");
            addKey(context, row3, "/", "/");
            addKey(context, row3, "\\", "\\");
            addKey(context, row3, "*", "*");
            addKey(context, row3, "$", "$");
            addKey(context, row3, "#", "#");

            addKey(context, row4, "CTRL+C", "\u0003");
            addKey(context, row4, "CTRL+V", "\u0016");
            addKey(context, row4, "CTRL+Z", "\u001a");
            addKey(context, row4, "CTRL+D", "\u0004");
            addKey(context, row4, "CTRL+A", "\u0001");
            addKey(context, row4, "CTRL+X", "\u0018");
            addKey(context, row4, "CTRL+S", "\u0013");
            addKey(context, row4, "CTRL+L", "\u000c");
            addKey(context, row4, "SPACE", " ", 2f, 72);
        }

        rootLayout.addView(row1);
        rootLayout.addView(row2);
        rootLayout.addView(row3);
        rootLayout.addView(row4);
        updateModifierIndicator();
    }

    /**
     * 处理按键事件，包括修饰键管理
     */
    private void handleKeyPress(String label, String value) {
        if ("LAY".equals(label)) {
            setLayoutMode((layoutMode + 1) % 3);
            return;
        }
        // 处理修饰键切换
        if ("CTRL".equals(label)) {
            isCtrlPressed = !isCtrlPressed;
            updateModifierIndicator();
            return;
        }
        if ("ALT".equals(label)) {
            isAltPressed = !isAltPressed;
            updateModifierIndicator();
            return;
        }
        if ("META".equals(label)) {
            isMetaPressed = !isMetaPressed;
            updateModifierIndicator();
            return;
        }
        // 应用修饰键到实际按键
        String result = value;

        if (isCtrlPressed || isAltPressed || isMetaPressed) {
            result = applyModifiers(value, isCtrlPressed, isAltPressed, isMetaPressed);
            isCtrlPressed = false;
            isAltPressed = false;
            isMetaPressed = false;
        }

        // 检查是否有自定义映射
        String mapped = customMapping.get(label);
        if (mapped != null) {
            result = mapped;
        }

        // 发送按键事件
        if (listener != null) {
            listener.onKeyClick(label, result);
        }

        // 更新修饰键状态显示
        updateModifierIndicator();
    }

    private void updateModifierIndicator() {
        updateKeyStyle(btnCtrl, isCtrlPressed);
        updateKeyStyle(btnAlt, isAltPressed);
        updateKeyStyle(btnMeta, isMetaPressed);
    }

    public void resetModifiers() {
        isCtrlPressed = false;
        isAltPressed = false;
        isMetaPressed = false;
        updateModifierIndicator();
    }

    public void setListener(OnKeypadClickListener listener) {
        this.listener = listener;
    }

    public void setLongClickListener(OnKeypadLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setLayoutChangeListener(OnLayoutChangeListener listener) {
        this.layoutChangeListener = listener;
    }

    public void setCustomMapping(java.util.Map<String, String> mapping) {
        customMapping.clear();
        if (mapping != null) {
            customMapping.putAll(mapping);
        }
    }
    
    public void setLayoutMode(int mode) {
        if (mode < 0 || mode > 2) mode = 0;
        if (layoutMode == mode) return;
        layoutMode = mode;
        buildLayout(getContext());
        if (layoutChangeListener != null) {
            layoutChangeListener.onLayoutChanged(layoutMode);
        }
    }
    
    /**
     * 重新加载键盘布局
     */
    public void reloadLayout() {
        // 清除现有布局
        this.removeAllViews();
        rootLayout.removeAllViews();
        buildLayout(getContext());
        this.addView(rootLayout);
    }

    private void updateKeyStyle(MaterialButton button, boolean active) {
        if (button == null) return;
        button.setTextColor(active ? keyTextActiveColor : keyTextColor);
        button.setBackgroundTintList(ColorStateList.valueOf(active ? keyBgActiveColor : keyBgColor));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String applyModifiers(String value, boolean ctrl, boolean alt, boolean meta) {
        boolean altLike = alt || meta;
        if (value.startsWith("\u001b[") && value.length() >= 3) {
            char last = value.charAt(value.length() - 1);
            int mod = ctrl && altLike ? 7 : ctrl ? 5 : altLike ? 3 : 1;
            if (mod > 1) {
                if (last == 'A' || last == 'B' || last == 'C' || last == 'D' || last == 'H' || last == 'F') {
                    return "\u001b[1;" + mod + last;
                }
                if (last == '~') {
                    String inner = value.substring(2, value.length() - 1);
                    return "\u001b[" + inner + ";" + mod + "~";
                }
            }
        }
        if (value.length() == 1) {
            char c = value.charAt(0);
            String base = value;
            if (ctrl && ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                base = String.valueOf((char) (c & 0x1f));
            }
            if (altLike) {
                return "\u001b" + base;
            }
            return base;
        }
        return value;
    }
}
