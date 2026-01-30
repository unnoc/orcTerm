package com.orcterm.ui.widget;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * 终端输入视图
 * 继承自 AppCompatEditText，用于拦截软键盘输入并转发给终端后端。
 * 通过自定义 InputConnection 实现对输入法的完全控制。
 * 阻止文本直接显示在视图中，而是将其转发。
 * 层级: UI 层 / 输入
 */
public class TerminalInputView extends AppCompatEditText {

    public interface OnKeyInputListener {
        void onInput(String text);
        void onKeyDown(int keyCode, KeyEvent event);
    }

    private OnKeyInputListener inputListener;

    public TerminalInputView(Context context) {
        super(context);
        init();
    }

    public TerminalInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TerminalInputView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 设置透明背景，不显示光标
        this.setBackground(null);
        this.setCursorVisible(false);
        this.setTextIsSelectable(false);
        // 重要: 设置为可见密码类型，可以防止大多数自动纠错/预测问题
        this.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        this.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE);
    }

    public void setOnKeyInputListener(OnKeyInputListener listener) {
        this.inputListener = listener;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_NONE;

        return new TerminalInputConnection(this, true);
    }
    
    // 拦截硬件按键事件 (例如物理键盘回车)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (inputListener != null) {
            inputListener.onKeyDown(keyCode, event);
            // 如果是我们应该拦截的键，返回 true 阻止系统处理
            if (shouldInterceptKey(keyCode)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 判断是否应该拦截此按键
     */
    private boolean shouldInterceptKey(int keyCode) {
        return keyCode >= KeyEvent.KEYCODE_F1 /*&& keyCode <= KeyEvent.KEYCODE_F20*/
            || keyCode == KeyEvent.KEYCODE_PAGE_UP
            || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
            || keyCode == KeyEvent.KEYCODE_MOVE_HOME
            || keyCode == KeyEvent.KEYCODE_MOVE_END
            /*|| keyCode == KeyEvent.KEYCODE_MOVE_FORWARD
            || keyCode == KeyEvent.KEYCODE_MOVE_BACKWARD*/
            || keyCode == KeyEvent.KEYCODE_DPAD_UP
            || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
            || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            || (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_DIVIDE);
    }

    /**
     * 自定义 InputConnection 以拦截软键盘事件
     */
    private class TerminalInputConnection extends android.view.inputmethod.BaseInputConnection {

        public TerminalInputConnection(android.view.View targetView, boolean fullEditor) {
            super(targetView, fullEditor);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (inputListener != null) {
                inputListener.onInput(text.toString());
            }
            // 不要真正将文本提交到 EditText
            return true; 
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            // 处理软键盘的退格键
            if (beforeLength > 0 && inputListener != null) {
                // 为每个要删除的字符发送 DEL 字符
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < beforeLength; i++) {
                    sb.append("\u007f"); // DEL
                }
                inputListener.onInput(sb.toString());
            }
            return true; // 阻止在 EditText 中实际删除 (反正它是空的)
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int keyCode = event.getKeyCode();
                String ansiSeq = getAnsiSequenceForKeyEvent(event);
                if (ansiSeq != null && inputListener != null) {
                    inputListener.onInput(ansiSeq);
                    return true;
                } else if (inputListener != null) {
                    inputListener.onKeyDown(keyCode, event);
                }
            }
            return super.sendKeyEvent(event);
        }

        /**
         * 获取按键事件的 ANSI 转义序列
         */
        private String getAnsiSequenceForKeyEvent(KeyEvent event) {
            int keyCode = event.getKeyCode();
            boolean ctrl = event.isCtrlPressed();
            boolean alt = event.isAltPressed();
            boolean shift = event.isShiftPressed();

            switch (keyCode) {
                case KeyEvent.KEYCODE_DEL:
                    return "\u007f";
                case KeyEvent.KEYCODE_FORWARD_DEL:
                    return ctrl ? "\033[3;5~" : "\033[3~";
                case KeyEvent.KEYCODE_ENTER:
                    return "\r";
                case KeyEvent.KEYCODE_TAB:
                    return ctrl ? "\033[Z" : "\t";
                case KeyEvent.KEYCODE_ESCAPE:
                    return "\033";
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (ctrl) return "\033[1;5A";
                    if (alt) return "\033[1;3A";
                    if (shift) return "\033[1;2A";
                    return "\033[A";
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (ctrl) return "\033[1;5B";
                    if (alt) return "\033[1;3B";
                    if (shift) return "\033[1;2B";
                    return "\033[B";
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (ctrl) return "\033[1;5C";
                    if (alt) return "\033[1;3C";
                    if (shift) return "\033[1;2C";
                    return "\033[C";
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (ctrl) return "\033[1;5D";
                    if (alt) return "\033[1;3D";
                    if (shift) return "\033[1;2D";
                    return "\033[D";
                case KeyEvent.KEYCODE_MOVE_HOME:
                    return ctrl ? "\033[1;5H" : "\033[H";
                case KeyEvent.KEYCODE_MOVE_END:
                    return ctrl ? "\033[1;5F" : "\033[F";
                case KeyEvent.KEYCODE_PAGE_UP:
                    return ctrl ? "\033[5;5~" : "\033[5~";
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    return ctrl ? "\033[6;5~" : "\033[6~";
                case KeyEvent.KEYCODE_INSERT:
                    return ctrl ? "\033[2;5~" : "\033[2~";
                default:
                    return null;
            }
        }
    }
}
