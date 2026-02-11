package com.orcterm.core.terminal;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * 终端仿真器
 * 管理终端屏幕状态（字符网格）并处理 ANSI 转义序列。
 * 负责将原始数据流解析为屏幕上的字符和样式。
 * 层级: 逻辑层 / 渲染支持
 */
public class TerminalEmulator {

    public interface ScrollbackListener {
        void onScrollbackLine(char[] chars, int[] styles);
    }

    private int columns;
    private int rows;

    /**
     * 脏区域跟踪类
     * 跟踪屏幕上发生变化的最小矩形区域
     */
    public static class DirtyRegion {
        int minX, maxX;
        int minY, maxY;
        boolean dirty;

        public DirtyRegion() {
            reset();
        }

        public void update(int x, int y) {
            if (!dirty) {
                minX = maxX = x;
                minY = maxY = y;
                dirty = true;
            } else {
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        public void updateRegion(int startX, int startY, int endX, int endY) {
            if (!dirty) {
                minX = startX;
                maxX = endX;
                minY = startY;
                maxY = endY;
                dirty = true;
            } else {
                minX = Math.min(minX, startX);
                maxX = Math.max(maxX, endX);
                minY = Math.min(minY, startY);
                maxY = Math.max(maxY, endY);
            }
        }

        public void updateAll(int width, int height) {
            if (!dirty) {
                minX = 0;
                maxX = width - 1;
                minY = 0;
                maxY = height - 1;
                dirty = true;
            } else {
                minX = 0;
                maxX = Math.max(maxX, width - 1);
                minY = 0;
                maxY = Math.max(maxY, height - 1);
            }
        }

        public void reset() {
            minX = 0;
            maxX = 0;
            minY = 0;
            maxY = 0;
            dirty = false;
        }

        public boolean isDirty() {
            return dirty;
        }

        public int getMinX() { return minX; }
        public int getMaxX() { return maxX; }
        public int getMinY() { return minY; }
        public int getMaxY() { return maxY; }
    }

    // 缓冲区
    private char[][] charBuffer; // 字符缓冲区
    private int[][] styleBuffer; // 样式缓冲区 (编码后的样式)

    // 脏区域跟踪
    private DirtyRegion dirtyRegion = new DirtyRegion();
    private ScrollbackListener scrollbackListener;

    // 光标位置
    private int cursorX = 0;
    private int cursorY = 0;
    private int savedCursorX = 0;
    private int savedCursorY = 0;
    private int scrollTop = 0;
    private int scrollBottom;
    private boolean cursorVisible = true;

    // 当前属性
    private int currentForeColor = 7; // 默认白色
    private int currentBackColor = 0; // 默认黑色
    private boolean isBold = false;
    private boolean isUnderline = false;
    private boolean isInverse = false;

    // 颜色模式支持
    private static final int COLOR_MODE_16 = 0;
    private static final int COLOR_MODE_256 = 1;
    private static final int COLOR_MODE_RGB = 2;
    private int currentForeColorMode = COLOR_MODE_16;
    private int currentBackColorMode = COLOR_MODE_16;
    
    // RGB颜色值（用于真彩色）
    private int currentRGBForeColor = 0;
    private int currentRGBBackColor = 0;

    // 解析状态机状态
    private static final int STATE_NORMAL = 0;
    private static final int STATE_ESC = 1;     // 收到 ESC
    private static final int STATE_CSI = 2;     // 收到 ESC [ (CSI)
    private int parseState = STATE_NORMAL;
    private StringBuilder csiParamBuffer = new StringBuilder();

    public TerminalEmulator(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        this.scrollBottom = rows - 1;
        // 初始化分配
        this.charBuffer = new char[rows][columns];
        this.styleBuffer = new int[rows][columns];
        int defaultStyle = encodeStyle(7, 0, false, false, false);
        for (int i = 0; i < rows; i++) {
            Arrays.fill(charBuffer[i], ' ');
            Arrays.fill(styleBuffer[i], defaultStyle);
        }
    }

    /**
     * 调整终端大小
     * 重新分配缓冲区并保留原有内容（左上角对齐）。
     *
     * @param newColumns 新列数
     * @param newRows    新行数
     */
    public synchronized void resize(int newColumns, int newRows) {
        if (this.columns == newColumns && this.rows == newRows) return;

        char[][] newCharBuffer = new char[newRows][newColumns];
        int[][] newStyleBuffer = new int[newRows][newColumns];
        
        int defaultStyle = encodeStyle(7, 0, false, false, false);

        // 初始化新缓冲区
        for (int i = 0; i < newRows; i++) {
            Arrays.fill(newCharBuffer[i], ' ');
            Arrays.fill(newStyleBuffer[i], defaultStyle);
        }

        // 复制现有数据 (左上角锚点)
        int copyRows = Math.min(this.rows, newRows);
        int copyCols = Math.min(this.columns, newColumns);

        for (int i = 0; i < copyRows; i++) {
            System.arraycopy(this.charBuffer[i], 0, newCharBuffer[i], 0, copyCols);
            System.arraycopy(this.styleBuffer[i], 0, newStyleBuffer[i], 0, copyCols);
        }

        this.columns = newColumns;
        this.rows = newRows;
        this.charBuffer = newCharBuffer;
        this.styleBuffer = newStyleBuffer;
        scrollTop = Math.max(0, Math.min(scrollTop, rows - 1));
        scrollBottom = Math.max(scrollTop, Math.min(scrollBottom, rows - 1));
        
        // 限制光标位置
        if (cursorX >= columns) cursorX = columns - 1;
        if (cursorY >= rows) cursorY = rows - 1;
    }

    /**
     * 编码颜色
     */
    private int encodeColor(int fg16, int bg16, int fgMode, int bgMode, int fgRGB, int bgRGB) {
        if (fgMode == COLOR_MODE_RGB) {
            return 0xC0000000 | fgRGB; // 使用最高位标识RGB颜色
        } else if (fgMode == COLOR_MODE_256) {
            return 0x80000000 | (fg16 & 0x1FF); // 使用次高位标识256色
        } else {
            return (fg16 & 0x1FF); // 标准16色
        }
    }

    /**
     * 编码样式
     * 将前景色、背景色和属性压缩到一个整数中。
     *
     * @param encodedColor 编码后的颜色值
     * @param bold      加粗
     * @param underline 下划线
     * @param inverse   反色
     * @return 编码后的样式整数
     */
    private int encodeStyle(int encodedColor, boolean bold, boolean underline, boolean inverse) {
        // bit 31: RGB flag, bit 30: 256-color flag, bit 29-21: unused, bit 20-0: attributes
        return (bold ? (1 << 20) : 0) |
               (underline ? (1 << 19) : 0) |
               (inverse ? (1 << 18) : 0) |
               (encodedColor & 0x3FFFF);
    }

    /**
     * Encode style attributes with separate foreground and background colors
     */
    private int encodeStyle(int foregroundColor, int backgroundColor, boolean bold, boolean underline, boolean inverse) {
        int encodedColors = (foregroundColor << 8) | backgroundColor;
        return encodeStyle(encodedColors, bold, underline, inverse);
    }

    /**
     * 追加数据
     * 处理输入字符串，更新屏幕状态。
     *
     * @param data 输入字符串
     */
    public synchronized void append(String data) {
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            processChar(c);
        }
    }

    /**
     * 处理单个字符
     * 根据当前解析状态处理字符（普通字符或控制序列）。
     *
     * @param c 输入字符
     */
    private void processChar(char c) {
        switch (parseState) {
            case STATE_NORMAL:
                if (c == 27) { // ESC
                    parseState = STATE_ESC;
                } else if (c == '\r') {
                    cursorX = 0;
                } else if (c == '\n') {
                    newLine();
                } else if (c == '\t') {
                    int next = ((cursorX / 8) + 1) * 8;
                    cursorX = Math.min(columns - 1, next);
                } else if (c == '\b') {
                    if (cursorX > 0) cursorX--;
                } else if (c == 7) { // Bell
                    // bell();
                } else if (c >= 32) { // 可打印字符
                    printChar(c);
                }
                break;

            case STATE_ESC:
                if (c == '[') {
                    parseState = STATE_CSI;
                    csiParamBuffer.setLength(0);
                } else if (c == 'c') {
                    resetTerminal();
                    parseState = STATE_NORMAL;
                } else if (c == 'D') {
                    newLine();
                    parseState = STATE_NORMAL;
                } else if (c == 'M') {
                    reverseIndex();
                    parseState = STATE_NORMAL;
                } else {
                    // 未知序列，重置或处理简单转义 (如 ESC M)
                    parseState = STATE_NORMAL; 
                }
                break;

            case STATE_CSI:
                if (Character.isDigit(c) || c == ';' || c == '?') {
                    csiParamBuffer.append(c);
                } else {
                    // 序列结束字符
                    processCsi(c, csiParamBuffer.toString());
                    parseState = STATE_NORMAL;
                }
                break;
        }
    }

    /**
     * 打印字符到屏幕
     * 更新当前光标位置的字符和样式。
     *
     * @param c 要打印的字符
     */
    private void printChar(char c) {
        if (cursorX >= columns) {
            cursorX = 0;
            newLine();
        }
        if (cursorY < rows && cursorX < columns) {
            charBuffer[cursorY][cursorX] = c;
            int encodedForeColor = encodeColor(currentForeColor, currentBackColor, currentForeColorMode, currentBackColorMode, 
                                                currentRGBForeColor, currentRGBBackColor);
            styleBuffer[cursorY][cursorX] = encodeStyle(encodedForeColor, isBold, isUnderline, isInverse);
            dirtyRegion.update(cursorX, cursorY);
            cursorX++;
        }
    }

    /**
     * 换行处理
     * 移动光标到下一行，如果到达底部则滚动屏幕。
     */
    private void newLine() {
        if (cursorY == scrollBottom) {
            scrollUpRegion(scrollTop, scrollBottom, 1);
        } else {
            cursorY++;
            if (cursorY >= rows) {
                cursorY = rows - 1;
            }
        }
    }

    private void reverseIndex() {
        if (cursorY == scrollTop) {
            scrollDownRegion(scrollTop, scrollBottom, 1);
        } else if (cursorY > 0) {
            cursorY--;
        }
    }

    /**
     * 向上滚动屏幕
     * 移除第一行，将所有行上移，并在底部添加空行。
     */
    private void scrollUpRegion(int top, int bottom, int count) {
        if (count <= 0 || top < 0 || bottom >= rows || top >= bottom) return;
        int defaultStyle = encodeStyle(currentForeColor, currentBackColor, isBold, isUnderline, isInverse);
        int regionHeight = bottom - top + 1;
        int shift = Math.min(count, regionHeight);
        if (scrollbackListener != null && top == 0) {
            for (int i = 0; i < shift; i++) {
                scrollbackListener.onScrollbackLine(
                    Arrays.copyOf(charBuffer[top + i], columns),
                    Arrays.copyOf(styleBuffer[top + i], columns)
                );
            }
        }
        for (int i = top; i <= bottom - shift; i++) {
            System.arraycopy(charBuffer[i + shift], 0, charBuffer[i], 0, columns);
            System.arraycopy(styleBuffer[i + shift], 0, styleBuffer[i], 0, columns);
        }
        for (int i = bottom - shift + 1; i <= bottom; i++) {
            Arrays.fill(charBuffer[i], ' ');
            Arrays.fill(styleBuffer[i], defaultStyle);
        }
        dirtyRegion.updateRegion(0, top, columns - 1, bottom);
    }

    private void scrollDownRegion(int top, int bottom, int count) {
        if (count <= 0 || top < 0 || bottom >= rows || top >= bottom) return;
        int defaultStyle = encodeStyle(currentForeColor, currentBackColor, isBold, isUnderline, isInverse);
        int regionHeight = bottom - top + 1;
        int shift = Math.min(count, regionHeight);
        for (int i = bottom; i >= top + shift; i--) {
            System.arraycopy(charBuffer[i - shift], 0, charBuffer[i], 0, columns);
            System.arraycopy(styleBuffer[i - shift], 0, styleBuffer[i], 0, columns);
        }
        for (int i = top; i < top + shift; i++) {
            Arrays.fill(charBuffer[i], ' ');
            Arrays.fill(styleBuffer[i], defaultStyle);
        }
        dirtyRegion.updateRegion(0, top, columns - 1, bottom);
    }

    /**
     * 处理 CSI 序列 (Control Sequence Introducer)
     * 根据参数和结束字符执行相应的终端控制命令。
     *
     * @param finalChar 序列结束字符 (命令类型)
     * @param params    参数字符串 (分号分隔)
     */
    private void processCsi(char finalChar, String params) {
        String[] parts = params.split(";");
        List<Integer> args = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) {
                try {
                    args.add(Integer.parseInt(p));
                } catch (NumberFormatException e) {
                    args.add(0);
                }
            }
        }
        if (args.isEmpty()) args.add(0);

        switch (finalChar) {
            case 'A': // Cursor Up
                cursorY = Math.max(0, cursorY - (args.get(0) == 0 ? 1 : args.get(0)));
                break;
            case 'B': // Cursor Down
                cursorY = Math.min(rows - 1, cursorY + (args.get(0) == 0 ? 1 : args.get(0)));
                break;
            case 'C': // Cursor Forward
                cursorX = Math.min(columns - 1, cursorX + (args.get(0) == 0 ? 1 : args.get(0)));
                break;
            case 'D': // Cursor Backward
                cursorX = Math.max(0, cursorX - (args.get(0) == 0 ? 1 : args.get(0)));
                break;
            case 'H':
            case 'f':
                setCursorPosition(args);
                break;
            case 'J':
                eraseInDisplay(args);
                break;
            case 'K':
                eraseInLine(args);
                break;
            case 'L':
                {
                    int n = args.get(0) == 0 ? 1 : args.get(0);
                    int topIns = Math.max(scrollTop, Math.min(cursorY, scrollBottom));
                    scrollDownRegion(topIns, scrollBottom, n);
                }
                break;
            case 'M':
                {
                    int n = args.get(0) == 0 ? 1 : args.get(0);
                    int topDel = Math.max(scrollTop, Math.min(cursorY, scrollBottom));
                    scrollUpRegion(topDel, scrollBottom, n);
                }
                break;
            case 'X':
                {
                    int n = args.get(0) == 0 ? 1 : args.get(0);
                    eraseChars(n);
                }
                break;
            case 'P':
                {
                    int n = args.get(0) == 0 ? 1 : args.get(0);
                    deleteChars(n);
                }
                break;
            case '@':
                {
                    int n = args.get(0) == 0 ? 1 : args.get(0);
                    insertChars(n);
                }
                break;
            case 'm':
                applySgr(args);
                break;
            case 's':
                savedCursorX = cursorX;
                savedCursorY = cursorY;
                break;
            case 'u':
                cursorX = savedCursorX;
                cursorY = savedCursorY;
                break;
            case 'S':
                scrollUpRegion(scrollTop, scrollBottom, args.get(0) == 0 ? 1 : args.get(0));
                break;
            case 'T':
                scrollDownRegion(scrollTop, scrollBottom, args.get(0) == 0 ? 1 : args.get(0));
                break;
            case 'r':
                int top = args.size() > 0 ? args.get(0) : 1;
                int bottom = args.size() > 1 ? args.get(1) : rows;
                if (top <= 0) top = 1;
                if (bottom <= 0) bottom = rows;
                if (top < bottom) {
                    scrollTop = Math.max(0, Math.min(rows - 1, top - 1));
                    scrollBottom = Math.max(scrollTop, Math.min(rows - 1, bottom - 1));
                    cursorX = 0;
                    cursorY = scrollTop;
                } else {
                    scrollTop = 0;
                    scrollBottom = rows - 1;
                    cursorX = 0;
                    cursorY = 0;
                }
                break;
            case 'h':
            case 'l':
                if (params.startsWith("?")) {
                    boolean set = finalChar == 'h';
                    String p = params.substring(1);
                    String[] nums = p.split(";");
                    for (String numStr : nums) {
                        if (numStr.isEmpty()) continue;
                        int val;
                        try {
                            val = Integer.parseInt(numStr);
                        } catch (NumberFormatException e) {
                            val = 0;
                        }
                        if (val == 25) {
                            cursorVisible = set;
                        }
                    }
                }
                break;
        }
    }

    private void setCursorPosition(List<Integer> args) {
        int r = args.size() > 0 ? args.get(0) : 1;
        int c = args.size() > 1 ? args.get(1) : 1;
        if (r <= 0) r = 1;
        if (c <= 0) c = 1;
        cursorY = Math.max(0, Math.min(rows - 1, r - 1));
        cursorX = Math.max(0, Math.min(columns - 1, c - 1));
    }

    private void eraseInDisplay(List<Integer> args) {
        int mode = args.size() > 0 ? args.get(0) : 0;
        int defStyle = encodeStyle(currentForeColor, currentBackColor, isBold, isUnderline, isInverse);
        if (mode == 2) {
            for (int y = 0; y < rows; y++) {
                Arrays.fill(charBuffer[y], ' ');
                Arrays.fill(styleBuffer[y], defStyle);
            }
            dirtyRegion.updateAll(columns, rows);
            return;
        }
        if (mode == 1) {
            for (int y = 0; y <= cursorY && y < rows; y++) {
                int endX = y == cursorY ? cursorX : columns - 1;
                for (int x = 0; x <= endX && x < columns; x++) {
                    charBuffer[y][x] = ' ';
                    styleBuffer[y][x] = defStyle;
                }
            }
            dirtyRegion.updateRegion(0, 0, cursorX, cursorY);
            return;
        }
        for (int y = cursorY; y < rows; y++) {
            int startX = y == cursorY ? cursorX : 0;
            for (int x = startX; x < columns; x++) {
                charBuffer[y][x] = ' ';
                styleBuffer[y][x] = defStyle;
            }
        }
        dirtyRegion.updateRegion(cursorX, cursorY, columns - 1, rows - 1);
    }

    private void eraseInLine(List<Integer> args) {
        int mode = args.size() > 0 ? args.get(0) : 0;
        int defStyle = encodeStyle(currentForeColor, currentBackColor, isBold, isUnderline, isInverse);
        if (cursorY < 0 || cursorY >= rows) return;
        if (mode == 2) {
            Arrays.fill(charBuffer[cursorY], ' ');
            Arrays.fill(styleBuffer[cursorY], defStyle);
            dirtyRegion.updateRegion(0, cursorY, columns - 1, cursorY);
            return;
        }
        if (mode == 1) {
            for (int x = 0; x <= cursorX && x < columns; x++) {
                charBuffer[cursorY][x] = ' ';
                styleBuffer[cursorY][x] = defStyle;
            }
            dirtyRegion.updateRegion(0, cursorY, cursorX, cursorY);
            return;
        }
        for (int x = cursorX; x < columns; x++) {
            charBuffer[cursorY][x] = ' ';
            styleBuffer[cursorY][x] = defStyle;
        }
        dirtyRegion.updateRegion(cursorX, cursorY, columns - 1, cursorY);
    }

    private void applySgr(List<Integer> args) {
        if (args.isEmpty()) {
            resetSgr();
            return;
        }
        for (int i = 0; i < args.size(); i++) {
            int code = args.get(i);
            if (code == 0) {
                resetSgr();
            } else if (code == 1) {
                isBold = true;
            } else if (code == 4) {
                isUnderline = true;
            } else if (code == 7) {
                isInverse = true;
            } else if (code == 22) {
                isBold = false;
            } else if (code == 24) {
                isUnderline = false;
            } else if (code == 27) {
                isInverse = false;
            } else if (code == 39) {
                currentForeColor = 7;
            } else if (code == 49) {
                currentBackColor = 0;
            } else if (code >= 30 && code <= 37) {
                currentForeColor = code - 30;
            } else if (code >= 40 && code <= 47) {
                currentBackColor = code - 40;
            } else if (code >= 90 && code <= 97) {
                currentForeColor = code - 90 + 8;
            } else if (code >= 100 && code <= 107) {
                currentBackColor = code - 100 + 8;
                } else if ((code == 38 || code == 48) && i + 2 < args.size() && args.get(i + 1) == 5) {
                    int colorIndex = map256Color(args.get(i + 2));
                    if (code == 38) {
                        currentForeColor = colorIndex;
                        currentForeColorMode = COLOR_MODE_256;
                    } else {
                        currentBackColor = colorIndex;
                        currentBackColorMode = COLOR_MODE_256;
                    }
                    i += 2;
                } else if ((code == 38 || code == 48) && i + 4 < args.size() && args.get(i + 1) == 2) {
                    // RGB颜色模式: CSI 38;2;R;G;Bm 或 CSI 48;2;R;G;Bm
                    if (i + 4 < args.size()) {
                        int r = Math.max(0, Math.min(255, args.get(i + 2)));
                        int g = Math.max(0, Math.min(255, args.get(i + 3)));
                        int b = Math.max(0, Math.min(255, args.get(i + 4)));
                        int rgbColor = 0xFF000000 | (r << 16) | (g << 8) | b;
                        
                        if (code == 38) {
                            currentRGBForeColor = rgbColor;
                            currentForeColorMode = COLOR_MODE_RGB;
                        } else {
                            currentRGBBackColor = rgbColor;
                            currentBackColorMode = COLOR_MODE_RGB;
                        }
                        i += 4;
                    }
                }
        }
    }

    private void resetSgr() {
        currentForeColor = 7;
        currentBackColor = 0;
        currentForeColorMode = COLOR_MODE_16;
        currentBackColorMode = COLOR_MODE_16;
        currentRGBForeColor = 0;
        currentRGBBackColor = 0;
        isBold = false;
        isUnderline = false;
        isInverse = false;
    }

    private int map256Color(int color) {
        if (color < 0) return 7;
        if (color < 16) return color;
        if (color < 232) {
            int index = color - 16;
            int r = (index / 36) % 6;
            int g = (index / 6) % 6;
            int b = index % 6;
            int red = r == 0 ? 0 : r * 40 + 55;
            int green = g == 0 ? 0 : g * 40 + 55;
            int blue = b == 0 ? 0 : b * 40 + 55;
            return (0xFF << 24) | (red << 16) | (green << 8) | blue;
        }
        if (color < 256) {
            int gray = (color - 232) * 10 + 8;
            return (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
        }
        return 7;
    }

    private void resetTerminal() {
        int defaultStyle = encodeStyle(7, 0, false, false, false);
        for (int i = 0; i < rows; i++) {
            Arrays.fill(charBuffer[i], ' ');
            Arrays.fill(styleBuffer[i], defaultStyle);
        }
        cursorX = 0;
        cursorY = 0;
        savedCursorX = 0;
        savedCursorY = 0;
        currentForeColor = 7;
        currentBackColor = 0;
        isBold = false;
        isUnderline = false;
        isInverse = false;
        scrollTop = 0;
        scrollBottom = rows - 1;
        cursorVisible = true;
        parseState = STATE_NORMAL;
        csiParamBuffer.setLength(0);
    }

    public char[][] getBuffer() {
        return charBuffer;
    }

    public int[][] getStyleBuffer() {
        return styleBuffer;
    }

    public DirtyRegion getDirtyRegion() {
        return dirtyRegion;
    }

    public void clearDirtyRegion() {
        dirtyRegion.reset();
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public int getCursorX() {
        return cursorX;
    }

    public int getCursorY() {
        return cursorY;
    }
    public boolean isCursorVisible() {
        return cursorVisible;
    }

    public void setScrollbackListener(ScrollbackListener listener) {
        this.scrollbackListener = listener;
    }

    private void eraseChars(int n) {
        if (n <= 0) return;
        int defStyle = encodeStyle(currentForeColor, currentBackColor, isBold, isUnderline, isInverse);
        int end = Math.min(columns, cursorX + n);
        for (int x = cursorX; x < end; x++) {
            charBuffer[cursorY][x] = ' ';
            styleBuffer[cursorY][x] = defStyle;
        }
        dirtyRegion.updateRegion(cursorX, cursorY, end - 1, cursorY);
    }

    private void deleteChars(int n) {
        if (n <= 0) return;
        int end = columns - n;
        if (end < cursorX) {
            eraseChars(columns - cursorX);
            return;
        }
        for (int x = cursorX; x < end; x++) {
            charBuffer[cursorY][x] = charBuffer[cursorY][x + n];
            styleBuffer[cursorY][x] = styleBuffer[cursorY][x + n];
        }
        int defStyle = encodeStyle(currentForeColor, currentBackColor, isBold, isUnderline, isInverse);
        for (int x = end; x < columns; x++) {
            charBuffer[cursorY][x] = ' ';
            styleBuffer[cursorY][x] = defStyle;
        }
    }

    private void insertChars(int n) {
        if (n <= 0) return;
        int start = Math.max(0, columns - n - 1);
        for (int x = start; x >= cursorX; x--) {
            int src = x;
            int dst = x + n;
            if (dst >= columns) continue;
            charBuffer[cursorY][dst] = charBuffer[cursorY][src];
            styleBuffer[cursorY][dst] = styleBuffer[cursorY][src];
        }
        int defStyle = encodeStyle(currentForeColor, currentBackColor, isBold, isUnderline, isInverse);
        int end = Math.min(columns, cursorX + n);
        for (int x = cursorX; x < end; x++) {
            charBuffer[cursorY][x] = ' ';
            styleBuffer[cursorY][x] = defStyle;
        }
    }

    /**
     * Write data to the terminal
     */
    public void write(String data) {
        if (data != null && data.length() > 0) {
            for (int i = 0; i < data.length(); i++) {
                processChar(data.charAt(i));
            }
        }
    }

/**
     * Get screen buffer for rendering
     */
    public ScreenBuffer getScreenBuffer() {
        return new ScreenBuffer(this);
    }

    /**
     * Screen buffer class for rendering
     */
    public static class ScreenBuffer {
        private final TerminalEmulator emulator;
        
        public ScreenBuffer(TerminalEmulator emulator) {
            this.emulator = emulator;
        }
        
        public int getRowCount() {
            return emulator.rows;
        }

        public int getColumnCount() {
            return emulator.columns;
        }

        public char getChar(int row, int col) {
            if (row < 0 || row >= emulator.rows || col < 0 || col >= emulator.columns) {
                return ' ';
            }
            return emulator.charBuffer[row][col];
        }

        public int getForegroundColor(int row, int col) {
            if (row < 0 || row >= emulator.rows || col < 0 || col >= emulator.columns) {
                return 7; // Default white
            }
            int style = emulator.styleBuffer[row][col];
            return decodeForegroundColor(style);
        }

        public int getBackgroundColor(int row, int col) {
            if (row < 0 || row >= emulator.rows || col < 0 || col >= emulator.columns) {
                return 0; // Default black
            }
            int style = emulator.styleBuffer[row][col];
            return decodeBackgroundColor(style);
        }
        
        private int decodeForegroundColor(int style) {
            int colorValue = (style >> 8) & 0xFF;  // Foreground is in bits 8-15
            return colorValue & 0xFF;
        }
        
        private int decodeBackgroundColor(int style) {
            int colorValue = style & 0xFF;  // Background is in bits 0-7
            return colorValue & 0xFF;
        }
    }

    

    }
