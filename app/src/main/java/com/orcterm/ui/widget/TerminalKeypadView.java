package com.orcterm.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.orcterm.R;

public class TerminalKeypadView extends HorizontalScrollView {

    public interface OnKeypadClickListener {
        void onKeyClick(String key, String value);
    }

    public interface OnKeypadLongClickListener {
        void onKeyLongClick(String key, String value);
    }

    private OnKeypadClickListener listener;
    private OnKeypadLongClickListener longClickListener;
    private LinearLayout rootLayout;
    private java.util.Map<String, String> customMapping = new java.util.HashMap<>();

    // 修饰键状态
    private boolean isCtrlPressed = false;
    private boolean isAltPressed = false;
    private boolean isMetaPressed = false;

    // 按钮引用，用于更新状态显示
    private Button btnCtrl;
    private Button btnAlt;
    private Button btnMeta;

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
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        addKey(context, row1, "ESC", "\u001b");
        addKey(context, row1, "TAB", "\t");
        btnCtrl = addKey(context, row1, "CTRL", "CTRL");
        btnAlt = addKey(context, row1, "ALT", "ALT");
        btnMeta = addKey(context, row1, "META", "\u001b");
        addKey(context, row1, "F1", "\u001bOP");
        addKey(context, row1, "F2", "\u001bOQ");
        addKey(context, row1, "F3", "\u001bOR");
        addKey(context, row1, "F4", "\u001bOS");
        addKey(context, row1, "F5", "\u001b[15~");
        addKey(context, row1, "F6", "\u001b[17~");
        addKey(context, row1, "F7", "\u001b[18~");
        addKey(context, row1, "F8", "\u001b[19~");
        addKey(context, row1, "F9", "\u001b[20~");
        addKey(context, row1, "F10", "\u001b[21~");
        addKey(context, row1, "HOME", "\u001b[1~");
        addKey(context, row1, "END", "\u001b[4~");
        addKey(context, row1, "PGUP", "\u001b[5~");
        addKey(context, row1, "PGDN", "\u001b[6~");
        addKey(context, row1, "INS", "\u001b[2~");
        addKey(context, row1, "DEL", "\u001b[3~");

        addKey(context, row2, "↑", "\u001b[A");
        addKey(context, row2, "↓", "\u001b[B");
        addKey(context, row2, "←", "\u001b[D");
        addKey(context, row2, "→", "\u001b[C");
        addKey(context, row2, "CTRL+C", "\u0003");
        addKey(context, row2, "CTRL+Z", "\u001a");
        addKey(context, row2, "CTRL+D", "\u0004");
        addKey(context, row2, "CTRL+L", "\u000c");
        addKey(context, row2, "|", "|");
        addKey(context, row2, "/", "/");
        addKey(context, row2, "-", "-");
        addKey(context, row2, "=", "=");
        addKey(context, row2, "~", "~");
        addKey(context, row2, "[", "[");
        addKey(context, row2, "]", "]");
        addKey(context, row2, "{", "{");
        addKey(context, row2, "}", "}");
        addKey(context, row2, "<", "<");
        addKey(context, row2, ">", ">");
        addKey(context, row2, "&", "&");
        addKey(context, row2, "+", "+");
        addKey(context, row2, "*", "*");
        addKey(context, row2, "%", "%");
        addKey(context, row2, "`", "`");

        rootLayout.addView(row1);
        rootLayout.addView(row2);
        this.addView(rootLayout);
        this.setFillViewport(true);
        this.setBackgroundColor(0xFF222222);
    }

    private Button addKey(Context context, LinearLayout parent, String label, String value) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setTransformationMethod(null); // Keep lowercase if needed
        btn.setPadding(16, 0, 16, 0);
        btn.setMinimumWidth(0);
        btn.setMinWidth(0);
        btn.setTextSize(12);
        btn.setTextColor(0xFFCCCCCC);
        btn.setBackgroundColor(0x00000000); // Transparent

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

    /**
     * 处理按键事件，包括修饰键管理
     */
    private void handleKeyPress(String label, String value) {
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

        if (isAltPressed && value.length() == 1) {
            // Alt + 字符 = ESC + 字符
            result = "\033" + value;
            isAltPressed = false; // Alt 单次模式
        } else if (isCtrlPressed && value.length() == 1) {
            char c = value.charAt(0);
            if (c >= 'a' && c <= 'z') {
                // Ctrl + 字母 = 控制字符
                result = String.valueOf((char)(c - 'a' + 1));
                isCtrlPressed = false; // Ctrl 单次模式
            } else if (c >= 'A' && c <= 'Z') {
                result = String.valueOf((char)(c - 'A' + 1));
                isCtrlPressed = false;
            }
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

    /**
     * 更新修饰键按钮的视觉状态
     */
    private void updateModifierIndicator() {
        if (btnCtrl != null) {
            btnCtrl.setTextColor(isCtrlPressed ? 0xFF00FF00 : 0xFFCCCCCC);
        }
        if (btnAlt != null) {
            btnAlt.setTextColor(isAltPressed ? 0xFF00FF00 : 0xFFCCCCCC);
        }
        if (btnMeta != null) {
            btnMeta.setTextColor(isMetaPressed ? 0xFF00FF00 : 0xFFCCCCCC);
        }
    }

    /**
     * 重置所有修饰键状态
     */
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

    public void setCustomMapping(java.util.Map<String, String> mapping) {
        customMapping.clear();
        if (mapping != null) {
            customMapping.putAll(mapping);
        }
    }
}
