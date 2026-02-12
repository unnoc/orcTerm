package com.orcterm.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import com.orcterm.R;
import com.orcterm.core.terminal.TerminalEmulator;

/**
 * 终端渲染视图组件
 * Terminal rendering view component
 * Responsible for rendering TerminalEmulator character buffer to Canvas
 * Supports ANSI color parsing and cursor drawing
 * Layer: UI/Rendering
 */
public class TerminalView extends View {

    private TerminalEmulator emulator;
    private Paint textPaint;
    private Paint bgPaint;
    private Paint cursorPaint;
    private float charWidth;
    private float charHeight;
    private int fontSize = 36; // px
    private float lineHeightMultiplier = 1.0f;
    private float letterSpacing = 0.0f;
    private String searchQuery;
    private int searchHighlightColor = 0x66FFD54F;
    private Bitmap backgroundImage;
    private int backgroundAlpha = 255;
    private Paint backgroundImagePaint;
    private GestureDetector gestureDetector;
    private OnTerminalGestureListener gestureListener;
    private Paint selectionPaint;
    private Paint lineNumberPaint;
    private Paint bracketMatchPaint;
    private Paint highRiskPaint;
    private Paint scrollHintPaint;
    private Paint scrollHintTextPaint;
    private Paint searchHighlightPaint;
    // 惯性滚动控制器
    private OverScroller scroller;
    // 触控速度追踪器
    private VelocityTracker velocityTracker;
    // 最小触发惯性速度
    private int minFlingVelocity;
    // 最大触发惯性速度
    private int maxFlingVelocity;
    // 上一次触控的 Y 坐标
    private float lastScrollY;
    // 是否处于惯性滚动中
    private boolean isFlinging;
    private boolean selectionActive;
    private int selectionStartRow;
    private int selectionStartCol;
    private int selectionEndRow;
    private int selectionEndCol;
    private int activePointerCount;
    private int lastRequestedCol = -1;
    private int lastRequestedRow = -1;
    private boolean threeFingerActive;
    private boolean threeFingerTriggered;
    private float threeFingerStartX;
    private int threeFingerThresholdPx;
    private boolean showLineNumbers;
    private boolean showBracketMatch;
    private boolean showHighRiskHighlight;
    private int lineNumberColor = 0xFF888888;
    private int bracketMatchColor = 0x66FFD54F;
    private int highRiskHighlightColor = 0x55FF5252;
    private String pendingPreviewContent;
    private String scrollHintText;
    private ScrollbackLine[] scrollbackBuffer = null;
    private int scrollbackHead = 0;
    private int scrollbackCount = 0;
    private char[] textRunBuffer = new char[0];
    private String[] visibleLineCache;
    private Boolean[] visibleHighRiskCache;
    private int[][] visibleSearchMatchCache;
    private int[] visibleSearchMatchCount;
    private String visibleSearchQueryLower;
    private int visibleCacheBaseRow = -1;
    private int visibleCacheCols = -1;
    private int visibleCacheRows = -1;
    private int scrollOffsetLines = 0;
    private int maxScrollbackLines = 2000;

    // ANSI color table (0-15 for 16-color, 16-255 for 256-color)
    private int[] colors = new int[256];
    
    // Rendering optimization: avoid creating String objects per character
    private char[] charArray = new char[1];
    
    // Rendering optimization: Paint cache
    private java.util.Map<Integer, Paint> colorPaintCache = new java.util.HashMap<>();

    private static class ScrollbackLine {
        private final char[] chars;
        private final int[] styles;

        private ScrollbackLine(char[] chars, int[] styles) {
            this.chars = chars;
            this.styles = styles;
        }
    }

    // Cursor style related fields
    public enum CursorStyle {
        BLOCK,
        UNDERLINE,
        BAR
    }
    
    private CursorStyle cursorStyle = CursorStyle.BLOCK;
    private boolean cursorBlink = true;
    private int cursorColor = 0xFFFFFFFF;
    private long cursorBlinkStart = System.currentTimeMillis();
    private static final long CURSOR_BLINK_INTERVAL = 500; // ms
    // 惯性滚动越界距离（像素）
    private static final int FLING_OVERSCROLL_DISTANCE = 0;
    // 惯性滚动回弹距离（像素）
    private static final int FLING_OVERFLING_DISTANCE = 0;

    /**
     * Background scaling mode
     */
    public enum BackgroundScale {
        CENTER,        // Center display, no scaling
        CENTER_CROP,   // Center crop, fill screen
        FIT_CENTER,    // Fit center, keep aspect ratio
        FILL           // Stretch fill, ignore aspect ratio
    }
    
    private BackgroundScale backgroundScale = BackgroundScale.FILL;

    // Double buffering related fields
    private Canvas offscreenCanvas;
    private Bitmap offscreenBitmap;
    private boolean needsFullRedraw = true;

    /**
     * Resize listener interface
     */
    public interface OnResizeListener {
        void onResize(int cols, int rows);
    }

    private OnResizeListener resizeListener;

    public interface OnTerminalGestureListener {
        void onDoubleTap();
        void onThreeFingerSwipe(int direction);
        void onCursorMoveRequest(int column, int row);
        void onTerminalTap(float x, float y);
        void onTerminalLongPress(float x, float y);
        void onTerminalKeyDown(int keyCode, KeyEvent event);
    }

    public void setOnResizeListener(OnResizeListener listener) {
        this.resizeListener = listener;
    }

    public void setOnTerminalGestureListener(OnTerminalGestureListener listener) {
        this.gestureListener = listener;
    }

    /**
     * Set font size
     *
     * @param sizePx Font size in pixels
     */
    public void setFontSize(int sizePx) {
        if (this.fontSize != sizePx) {
            this.fontSize = sizePx;
            textPaint.setTextSize(fontSize);
            if (lineNumberPaint != null) {
                lineNumberPaint.setTextSize(textPaint.getTextSize());
            }
            updateMetrics();
            
            // Trigger resize
            if (getWidth() > 0 && getHeight() > 0) {
                int cols = (int) (getWidth() / charWidth);
                int rows = (int) (getHeight() / charHeight);
                if (cols > 0 && rows > 0) {
                    if (emulator != null) emulator.resize(cols, rows);
                    if (resizeListener != null) resizeListener.onResize(cols, rows);
                }
            }
            postInvalidate();
        }
    }

    /**
     * Set color scheme
     *
     * @param newColors 16-color array
     */
    public void setColorScheme(int[] newColors) {
        if (newColors != null && newColors.length >= 16) {
            System.arraycopy(newColors, 0, this.colors, 0, 16);
            // Update background color (usually color 0)
            setBackgroundColor(this.colors[0]);
            // Clear color cache
            clearColorCache();
            postInvalidate();
        }
    }

    public void setTypeface(Typeface typeface) {
        if (typeface != null) {
            textPaint.setTypeface(typeface);
            if (lineNumberPaint != null) {
                lineNumberPaint.setTypeface(typeface);
            }
            updateMetrics();
            postInvalidate();
        }
    }

    public void setLineHeightMultiplier(float multiplier) {
        if (multiplier > 0.5f && multiplier <= 3.0f) {
            this.lineHeightMultiplier = multiplier;
            updateMetrics();
            postInvalidate();
        }
    }

    public void setLetterSpacing(float spacing) {
        this.letterSpacing = spacing;
        textPaint.setLetterSpacing(letterSpacing);
        if (lineNumberPaint != null) {
            lineNumberPaint.setLetterSpacing(letterSpacing);
        }
        updateMetrics();
        postInvalidate();
    }

    public void setBackgroundImage(Bitmap bitmap) {
        this.backgroundImage = bitmap;
        if (bitmap != null && !bitmap.isRecycled()) {
            this.backgroundImagePaint = new Paint();
            this.backgroundImagePaint.setAlpha(backgroundAlpha);
        }
        postInvalidate();
    }

    public void setBackgroundAlpha(int alpha) {
        if (alpha < 0) {
            this.backgroundAlpha = 0;
        } else if (alpha > 255) {
            this.backgroundAlpha = 255;
        } else {
            this.backgroundAlpha = alpha;
        }
        if (backgroundImagePaint != null) {
            backgroundImagePaint.setAlpha(this.backgroundAlpha);
        }
    }

    public void setBackgroundScale(BackgroundScale scale) {
        if (this.backgroundScale != scale) {
            this.backgroundScale = scale;
            postInvalidate();
        }
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query;
        clearVisibleSearchCache();
        postInvalidate();
    }

    public void clearSearchQuery() {
        this.searchQuery = null;
        clearVisibleSearchCache();
        postInvalidate();
    }
    
    public void scrollToFirstMatch(String query) {
        if (query == null || query.isEmpty() || emulator == null) return;
        
        // 该功能需要终端模拟器支持滚动到指定内容
        // 目前的实现仅作为占位符
        // TODO: 实现滚动到第一个匹配项的功能
    }
    
    public int getMatchCount(String query) {
        if (query == null || query.isEmpty() || emulator == null) return 0;
        int rows = getVisibleRowCount();
        int cols = getVisibleColumnCount();
        String lowerQuery = query.toLowerCase(java.util.Locale.getDefault());
        int count = 0;
        
        for (int row = 0; row < rows; row++) {
            String line = buildDisplayLine(row, cols);
            String lowerLine = line.toLowerCase(java.util.Locale.getDefault());
            
            int startIndex = 0;
            while (startIndex < line.length()) {
                int matchIndex = lowerLine.indexOf(lowerQuery, startIndex);
                if (matchIndex == -1) break; // 没有找到更多匹配项
                count++;
                startIndex = matchIndex + 1; // 移动到下一个可能的匹配位置
            }
        }
        
        return count;
    }

    public void setSelectionColor(int color) {
        if (selectionPaint != null) {
            selectionPaint.setColor(color);
            postInvalidate();
        }
    }

    public void setSearchHighlightColor(int color) {
        this.searchHighlightColor = color;
        if (searchHighlightPaint != null) {
            searchHighlightPaint.setColor(color);
        }
        postInvalidate();
    }

    public void setShowLineNumbers(boolean show) {
        this.showLineNumbers = show;
        postInvalidate();
    }

    public void setLineNumberColor(int color) {
        this.lineNumberColor = color;
        if (lineNumberPaint != null) {
            lineNumberPaint.setColor(color);
        }
        postInvalidate();
    }

    public void setShowBracketMatch(boolean show) {
        this.showBracketMatch = show;
        postInvalidate();
    }

    public void setBracketMatchColor(int color) {
        this.bracketMatchColor = color;
        if (bracketMatchPaint != null) {
            bracketMatchPaint.setColor(color);
        }
        postInvalidate();
    }

    public void setShowHighRiskHighlight(boolean show) {
        this.showHighRiskHighlight = show;
        postInvalidate();
    }

    public void setHighRiskHighlightColor(int color) {
        this.highRiskHighlightColor = color;
        if (highRiskPaint != null) {
            highRiskPaint.setColor(color);
        }
        postInvalidate();
    }

    public void setPreviewContent(String content) {
        pendingPreviewContent = content;
        if (content == null) return;
        if (getWidth() > 0 && getHeight() > 0) {
            applyPreviewContent(content);
        }
    }
    
    public String getTerminalContent() {
        if (emulator == null) return "";
        
        TerminalEmulator.ScreenBuffer buffer = emulator.getScreenBuffer();
        if (buffer == null) return "";
        
        int rows = buffer.getRowCount();
        StringBuilder content = new StringBuilder();
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < buffer.getColumnCount(); col++) {
                char c = buffer.getChar(row, col);
                if (c != 0) { // 只添加非空字符
                    content.append(c);
                }
            }
            // 在每行末尾添加换行符（除非是最后一行）
            if (row < rows - 1) {
                content.append('\n');
            }
        }
        
        return content.toString();
    }

    public TerminalView(Context context) {
        super(context);
        init();
    }

    public TerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint = new Paint();
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(fontSize);
        textPaint.setAntiAlias(true);

        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);

        cursorPaint = new Paint();
        cursorPaint.setColor(cursorColor);
        cursorPaint.setAlpha(128); // Semi-transparent block
        backgroundImagePaint = new Paint();
        selectionPaint = new Paint();
        selectionPaint.setColor(0x5533B5E5);
        searchHighlightPaint = new Paint();
        searchHighlightPaint.setColor(searchHighlightColor);
        searchHighlightPaint.setStyle(Paint.Style.FILL);
        lineNumberPaint = new Paint(textPaint);
        lineNumberPaint.setColor(lineNumberColor);
        bracketMatchPaint = new Paint();
        bracketMatchPaint.setColor(bracketMatchColor);
        highRiskPaint = new Paint();
        highRiskPaint.setColor(highRiskHighlightColor);
        scrollHintPaint = new Paint();
        scrollHintPaint.setColor(0xAA000000);
        scrollHintPaint.setStyle(Paint.Style.FILL);
        scrollHintTextPaint = new Paint(textPaint);
        scrollHintTextPaint.setColor(Color.WHITE);
        scrollHintTextPaint.setTextSize(dpToPx(11));
        scrollHintText = getResources().getString(R.string.terminal_scrollback_hint);
        scroller = new OverScroller(getContext());
        ViewConfiguration configuration = ViewConfiguration.get(getContext());
        minFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maxFlingVelocity = configuration.getScaledMaximumFlingVelocity();

        initColors();

        updateMetrics();

        setBackgroundColor(colors[0]);
        setClickable(true);
        threeFingerThresholdPx = dpToPx(80);
        
        // 创建一个自定义的GestureDetector来处理长按事件
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                clearSelection();
                if (gestureListener != null) {
                    gestureListener.onTerminalTap(e.getX(), e.getY());
                }
                performClick();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (gestureListener != null) {
                    gestureListener.onDoubleTap();
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }
            
            @Override
            public void onLongPress(MotionEvent e) {
                if (gestureListener != null) {
                    gestureListener.onTerminalLongPress(e.getX(), e.getY());
                }
            }
        });
        
        // 设置长按检测
        gestureDetector.setIsLongpressEnabled(true);
    }

    public void attachEmulator(TerminalEmulator emulator) {
        this.emulator = emulator;
        resetScrollback();
        if (this.emulator != null) {
            this.emulator.setScrollbackListener(this::addScrollbackLine);
        }
        if (getWidth() > 0 && getHeight() > 0) {
            int cols = (int) (getWidth() / charWidth);
            int rows = (int) (getHeight() / charHeight);
            if (cols > 0 && rows > 0) {
                this.emulator.resize(cols, rows);
            }
        }
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        if (emulator != null && w > 0 && h > 0) {
            int cols = (int) (w / charWidth);
            int rows = (int) (h / charHeight);
            if (cols > 0 && rows > 0) {
                emulator.resize(cols, rows);
                if (resizeListener != null) resizeListener.onResize(cols, rows);
            }
        }
        if (pendingPreviewContent != null && w > 0 && h > 0) {
            applyPreviewContent(pendingPreviewContent);
        }
        setScrollOffset(scrollOffsetLines);
    }

    public void append(String data) {
        if (emulator != null) {
            emulator.write(data);
            invalidateDirtyRegion();
        }
    }

    /**
     * Notify view that screen content has been updated externally
     * (e.g. by TerminalSession writing directly to emulator)
     */
    public void notifyScreenUpdate() {
        invalidateDirtyRegion();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (emulator == null) {
            return;
        }

        // Draw background
        drawBackground(canvas);

        // Draw terminal content
        drawTerminalContent(canvas);

        // Draw selection
        drawSelection(canvas);

        // Draw search highlights
        if (searchQuery != null && !searchQuery.isEmpty()) {
            drawSearchHighlights(canvas);
        }

        // Draw cursor
        drawCursor(canvas);

        // Draw scroll hint when not at bottom
        drawScrollHint(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 检测长按事件
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
        
        activePointerCount = event.getPointerCount();
        int action = event.getActionMasked();
        
        if (activePointerCount == 2) {
            handleTwoFingerSelection(event, action);
            return true;
        }
        if (activePointerCount == 3) {
            handleThreeFingerSwipe(event, action);
            return true;
        }
        if (activePointerCount == 1 && !selectionActive) {
            handleSingleFingerScroll(event, action);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            lastRequestedCol = -1;
            lastRequestedRow = -1;
            threeFingerActive = false;
            threeFingerTriggered = false;
        }
        return true;
    }

    /**
     * Draw background
     */
    private void drawBackground(Canvas canvas) {
        // Draw background color
        bgPaint.setColor(colors[0]);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // Draw background image if set
        if (backgroundImage != null && !backgroundImage.isRecycled()) {
            drawBackgroundImage(canvas);
        }
    }

    /**
     * Draw background image with scaling
     */
    private void drawBackgroundImage(Canvas canvas) {
        Rect dstRect = calculateBackgroundImageRect();
        canvas.drawBitmap(backgroundImage, null, dstRect, backgroundImagePaint);
    }

    /**
     * Calculate background image destination rectangle based on scaling mode
     */
    private Rect calculateBackgroundImageRect() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int bitmapWidth = backgroundImage.getWidth();
        int bitmapHeight = backgroundImage.getHeight();

        int left, top, right, bottom;

        switch (backgroundScale) {
            case CENTER:
                left = (viewWidth - bitmapWidth) / 2;
                top = (viewHeight - bitmapHeight) / 2;
                right = left + bitmapWidth;
                bottom = top + bitmapHeight;
                break;
                
            case CENTER_CROP:
                float scale = Math.max(
                    (float) viewWidth / bitmapWidth,
                    (float) viewHeight / bitmapHeight
                );
                int scaledWidth = (int) (bitmapWidth * scale);
                int scaledHeight = (int) (bitmapHeight * scale);
                left = (viewWidth - scaledWidth) / 2;
                top = (viewHeight - scaledHeight) / 2;
                right = left + scaledWidth;
                bottom = top + scaledHeight;
                break;
                
            case FIT_CENTER:
                float fitScale = Math.min(
                    (float) viewWidth / bitmapWidth,
                    (float) viewHeight / bitmapHeight
                );
                int fitWidth = (int) (bitmapWidth * fitScale);
                int fitHeight = (int) (bitmapHeight * fitScale);
                left = (viewWidth - fitWidth) / 2;
                top = (viewHeight - fitHeight) / 2;
                right = left + fitWidth;
                bottom = top + fitHeight;
                break;
                
            case FILL:
            default:
                left = 0;
                top = 0;
                right = viewWidth;
                bottom = viewHeight;
                break;
        }

        return new Rect(left, top, right, bottom);
    }

    /**
     * Draw terminal content
     */
    private void drawTerminalContent(Canvas canvas) {
        TerminalEmulator.ScreenBuffer buffer = emulator.getScreenBuffer();
        if (buffer == null) return;

        int rows = getVisibleRowCount();
        int cols = getVisibleColumnCount();
        int baseRow = getDisplayBaseRow();
        int scrollbackCount = getScrollbackCount();

        int digits = showLineNumbers ? String.valueOf(rows).length() : 0;
        float lineNumberWidth = showLineNumbers ? (digits + 1) * charWidth : 0f;

        if (textRunBuffer.length < cols) {
            textRunBuffer = new char[cols];
        }
        for (int row = 0; row < rows; row++) {
            float y = row * charHeight;
            float xOffset = lineNumberWidth;
            float baseline = y + charHeight - (charHeight * 0.2f);
            if (showLineNumbers) {
                lineNumberPaint.setColor(lineNumberColor);
                String num = String.format(java.util.Locale.getDefault(), "%" + digits + "d", row + 1);
                canvas.drawText(num, 0, num.length(), 0, baseline, lineNumberPaint);
            }
            if (showHighRiskHighlight && isHighRiskLine(row, cols)) {
                highRiskPaint.setColor(highRiskHighlightColor);
                canvas.drawRect(xOffset, y, xOffset + cols * charWidth, y + charHeight, highRiskPaint);
            }
            float x = xOffset;
            Paint runPaint = null;
            int runLen = 0;
            float runStartX = x;
            
            for (int col = 0; col < cols; col++) {
                int globalRow = baseRow + row;
                char c;
                int fgColor;
                int bgColor;
                if (globalRow < scrollbackCount) {
                    ScrollbackLine line = getScrollbackLine(globalRow);
                    if (line == null) {
                        c = ' ';
                        fgColor = 7;
                        bgColor = 0;
                    } else {
                        int style = (col >= 0 && col < line.styles.length) ? line.styles[col] : 0;
                        c = (col >= 0 && col < line.chars.length) ? line.chars[col] : ' ';
                        fgColor = decodeForegroundColor(style);
                        bgColor = decodeBackgroundColor(style);
                    }
                } else {
                    int bufferRow = globalRow - scrollbackCount;
                    c = buffer.getChar(bufferRow, col);
                    fgColor = buffer.getForegroundColor(bufferRow, col);
                    bgColor = buffer.getBackgroundColor(bufferRow, col);
                }
                
                // Map color indices to actual colors
                int actualFgColor = getColorFromIndex(fgColor);
                int actualBgColor = getColorFromIndex(bgColor);
                
                // Handle special case where bg color is same as default bg
                if (actualBgColor == colors[0]) {
                    actualBgColor = Color.TRANSPARENT;
                }

                // Only draw background if it's explicitly set (not default/transparent)
                if (actualBgColor != Color.TRANSPARENT) {
                    bgPaint.setColor(actualBgColor);
                    canvas.drawRect(x, y, x + charWidth, y + charHeight, bgPaint);
                }

                if (showBracketMatch && isBracket(c)) {
                    bracketMatchPaint.setColor(bracketMatchColor);
                    canvas.drawRect(x, y, x + charWidth, y + charHeight, bracketMatchPaint);
                }
                
                // Only draw text for non-space characters
                if (c != ' ' && c != 0) {
                    int effectiveBg = actualBgColor == Color.TRANSPARENT ? colors[0] : actualBgColor;
                    if (!hasEnoughContrast(effectiveBg, actualFgColor)) {
                        actualFgColor = isDark(effectiveBg) ? Color.WHITE : Color.BLACK;
                    }
                    
                    Paint cachedPaint = getCachedPaint(actualFgColor);
                    if (runPaint == cachedPaint) {
                        textRunBuffer[runLen++] = c;
                    } else {
                        if (runLen > 0 && runPaint != null) {
                            canvas.drawText(textRunBuffer, 0, runLen, runStartX, baseline, runPaint);
                        }
                        runPaint = cachedPaint;
                        runLen = 0;
                        runStartX = x;
                        textRunBuffer[runLen++] = c;
                    }
                } else {
                    if (runLen > 0 && runPaint != null) {
                        canvas.drawText(textRunBuffer, 0, runLen, runStartX, baseline, runPaint);
                        runLen = 0;
                        runPaint = null;
                    }
                }
                
                x += charWidth;
            }
            if (runLen > 0 && runPaint != null) {
                canvas.drawText(textRunBuffer, 0, runLen, runStartX, baseline, runPaint);
            }
        }
    }

    private void drawSelection(Canvas canvas) {
        if (!selectionActive || emulator == null) {
            return;
        }
        int rows = emulator.getRows();
        int cols = emulator.getColumns();
        if (rows <= 0 || cols <= 0) return;
        float xOffset = getLineNumberOffset(rows);
        boolean forward = selectionStartRow < selectionEndRow
            || (selectionStartRow == selectionEndRow && selectionStartCol <= selectionEndCol);
        int startRow = Math.max(0, Math.min(rows - 1, selectionStartRow));
        int endRow = Math.max(0, Math.min(rows - 1, selectionEndRow));
        int minRow = Math.min(startRow, endRow);
        int maxRow = Math.max(startRow, endRow);
        for (int row = minRow; row <= maxRow; row++) {
            int startCol;
            int endCol;
            if (forward) {
                if (row == selectionStartRow && row == selectionEndRow) {
                    startCol = Math.min(selectionStartCol, selectionEndCol);
                    endCol = Math.max(selectionStartCol, selectionEndCol);
                } else if (row == selectionStartRow) {
                    startCol = selectionStartCol;
                    endCol = cols - 1;
                } else if (row == selectionEndRow) {
                    startCol = 0;
                    endCol = selectionEndCol;
                } else {
                    startCol = 0;
                    endCol = cols - 1;
                }
            } else {
                if (row == selectionStartRow && row == selectionEndRow) {
                    startCol = Math.min(selectionStartCol, selectionEndCol);
                    endCol = Math.max(selectionStartCol, selectionEndCol);
                } else if (row == selectionEndRow) {
                    startCol = selectionEndCol;
                    endCol = cols - 1;
                } else if (row == selectionStartRow) {
                    startCol = 0;
                    endCol = selectionStartCol;
                } else {
                    startCol = 0;
                    endCol = cols - 1;
                }
            }
            startCol = Math.max(0, Math.min(cols - 1, startCol));
            endCol = Math.max(0, Math.min(cols - 1, endCol));
            float left = xOffset + startCol * charWidth;
            float top = row * charHeight;
            float right = xOffset + (endCol + 1) * charWidth;
            float bottom = top + charHeight;
            canvas.drawRect(left, top, right, bottom, selectionPaint);
        }
    }

    /**
     * Draw search highlights
     */
    private void drawSearchHighlights(Canvas canvas) {
        if (searchQuery == null || searchQuery.isEmpty() || emulator == null) {
            return;
        }
        
        int rows = getVisibleRowCount();
        int cols = getVisibleColumnCount();
        String lowerSearch = searchQuery.toLowerCase(java.util.Locale.getDefault());
        float xOffset = getLineNumberOffset(rows);
        
        for (int row = 0; row < rows; row++) {
            int count = getSearchMatchesForRow(row, cols, lowerSearch);
            if (count <= 0) continue;
            int[] matches = visibleSearchMatchCache[row];
            int searchLen = searchQuery.length();
            int startIndex = matches[0];
            int endIndex = startIndex + searchLen;
            for (int i = 1; i < count; i++) {
                int matchIndex = matches[i];
                int matchEnd = matchIndex + searchLen;
                if (matchIndex <= endIndex) {
                    endIndex = Math.max(endIndex, matchEnd);
                } else {
                    float left = xOffset + startIndex * charWidth;
                    float top = row * charHeight;
                    float right = xOffset + endIndex * charWidth;
                    float bottom = top + charHeight;
                    canvas.drawRect(left, top, right, bottom, searchHighlightPaint);
                    startIndex = matchIndex;
                    endIndex = matchEnd;
                }
            }
            float left = xOffset + startIndex * charWidth;
            float top = row * charHeight;
            float right = xOffset + endIndex * charWidth;
            float bottom = top + charHeight;
            canvas.drawRect(left, top, right, bottom, searchHighlightPaint);
        }
    }

    /**
     * Draw cursor
     */
    private void drawCursor(Canvas canvas) {
        if (emulator == null || !emulator.isCursorVisible()) return;
        if (scrollOffsetLines > 0) return;

        // Handle blinking
        if (cursorBlink) {
            long now = System.currentTimeMillis();
            if ((now - cursorBlinkStart) / CURSOR_BLINK_INTERVAL % 2 == 0) {
                return; // Hide when blinking
            }
        }

        float cursorX = getLineNumberOffset(emulator.getRows()) + emulator.getCursorX() * charWidth;
        float cursorY = emulator.getCursorY() * charHeight;

        cursorPaint.setColor(cursorColor);

        switch (cursorStyle) {
            case BLOCK:
                cursorPaint.setAlpha(128); // Semi-transparent block
                canvas.drawRect(cursorX, cursorY, 
                             cursorX + charWidth, cursorY + charHeight, cursorPaint);
                break;
            case UNDERLINE:
                cursorPaint.setAlpha(255); // Solid underline
                float underlineY = cursorY + charHeight - 2;
                canvas.drawRect(cursorX, underlineY, 
                             cursorX + charWidth, underlineY + 2, cursorPaint);
                break;
            case BAR:
                cursorPaint.setAlpha(255); // Solid vertical bar
                float barX = cursorX + 1;
                canvas.drawRect(barX, cursorY, 
                             barX + 2, cursorY + charHeight, cursorPaint);
                break;
        }
    }

    private void drawScrollHint(Canvas canvas) {
        if (scrollOffsetLines <= 0 || scrollHintPaint == null || scrollHintTextPaint == null) return;
        String text = scrollHintText;
        if (text == null || text.isEmpty()) {
            text = getResources().getString(R.string.terminal_scrollback_hint);
        }
        float padding = dpToPx(6);
        float textWidth = scrollHintTextPaint.measureText(text);
        float textHeight = scrollHintTextPaint.getTextSize();
        float right = getWidth() - padding;
        float left = right - textWidth - padding * 2;
        float top = padding;
        float bottom = top + textHeight + padding * 1.5f;
        float radius = dpToPx(6);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, scrollHintPaint);
        canvas.drawText(text, left + padding, top + textHeight, scrollHintTextPaint);
    }

    /**
     * Get cached Paint for color or create new one
     */
    private Paint getCachedPaint(int color) {
        Paint paint = colorPaintCache.get(color);
        if (paint == null) {
            paint = new Paint(textPaint);
            paint.setColor(color);
            colorPaintCache.put(color, paint);
        }
        return paint;
    }

    /**
     * Clear color paint cache
     */
    private void clearColorCache() {
        colorPaintCache.clear();
    }

    /**
     * Initialize default ANSI colors
     */
    private void initColors() {
        // Standard 16 colors
        colors[0] = Color.BLACK;      // Black
        colors[1] = Color.RED;        // Red
        colors[2] = Color.GREEN;      // Green
        colors[3] = Color.YELLOW;     // Yellow
        colors[4] = Color.BLUE;       // Blue
        colors[5] = Color.MAGENTA;    // Magenta
        colors[6] = Color.CYAN;       // Cyan
        colors[7] = Color.LTGRAY;     // Light Gray (White)
        
        // Bright colors (8-15)
        colors[8] = Color.DKGRAY;     // Dark Gray (Bright Black)
        colors[9] = 0xFFFF6B6B;      // Bright Red
        colors[10] = 0xFF69DB7C;     // Bright Green
        colors[11] = 0xFFFFF27D;    // Bright Yellow
        colors[12] = 0xFF6C9BD2;     // Bright Blue
        colors[13] = 0xFFDB8EF5;    // Bright Magenta
        colors[14] = 0xFF6DCFC7;    // Bright Cyan
        colors[15] = Color.WHITE;     // Bright White

        // Initialize 256-color palette (16-255)
        init256ColorPalette();
    }

    /**
     * Initialize 256-color palette
     */
    private void init256ColorPalette() {
        // Color cube: 16-231 (6x6x6 = 216 colors)
        int colorIndex = 16;
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 6; g++) {
                for (int b = 0; b < 6; b++) {
                    int red = r > 0 ? (r * 40 + 55) : 0;
                    int green = g > 0 ? (g * 40 + 55) : 0;
                    int blue = b > 0 ? (b * 40 + 55) : 0;
                    colors[colorIndex++] = Color.rgb(red, green, blue);
                }
            }
        }

        // Grayscale: 232-255
        for (int i = 0; i < 24; i++) {
            int gray = i * 10 + 8;
            colors[colorIndex++] = Color.rgb(gray, gray, gray);
        }
    }

    /**
     * Update text metrics after font changes
     */
    private void updateMetrics() {
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        charHeight = (fm.descent - fm.ascent) * lineHeightMultiplier;
        charWidth = textPaint.measureText("M") + letterSpacing;
    }

    /**
     * Invalidate dirty region for performance optimization
     */
    private void invalidateDirtyRegion() {
        if (emulator == null) {
            postInvalidate();
            return;
        }
        TerminalEmulator.DirtyRegion dirtyRegion = emulator.getDirtyRegion();
        if (dirtyRegion == null || !dirtyRegion.isDirty()) {
            return;
        }
        int rows = getVisibleRowCount();
        int cols = getVisibleColumnCount();
        if (rows <= 0 || cols <= 0) {
            postInvalidate();
            emulator.clearDirtyRegion();
            return;
        }
        int scrollbackCount = getScrollbackCount();
        int baseRow = getDisplayBaseRow();
        int visibleStart = baseRow;
        int visibleEnd = baseRow + rows - 1;
        int globalStart = scrollbackCount + dirtyRegion.getMinY();
        int globalEnd = scrollbackCount + dirtyRegion.getMaxY();
        int clippedStart = Math.max(visibleStart, globalStart);
        int clippedEnd = Math.min(visibleEnd, globalEnd);
        if (clippedStart > clippedEnd) {
            emulator.clearDirtyRegion();
            return;
        }
        int startRow = clippedStart - baseRow;
        int endRow = clippedEnd - baseRow;
        float xOffset = getLineNumberOffset(rows);
        int minX = Math.max(0, Math.min(cols - 1, dirtyRegion.getMinX()));
        int maxX = Math.max(0, Math.min(cols - 1, dirtyRegion.getMaxX()));
        float left = showLineNumbers ? 0f : xOffset + minX * charWidth;
        float right = xOffset + (maxX + 1) * charWidth;
        float top = startRow * charHeight;
        float bottom = (endRow + 1) * charHeight;
        postInvalidate((int) left, (int) top, (int) right, (int) bottom);
        invalidateVisibleLineCache(startRow, endRow);
        invalidateVisibleSearchCache(startRow, endRow);
        emulator.clearDirtyRegion();
    }

    /**
     * Check if there's enough contrast between foreground and background
     */
    private boolean hasEnoughContrast(int bg, int fg) {
        int bgLum = (int) (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg));
        int fgLum = (int) (0.299 * Color.red(fg) + 0.587 * Color.green(fg) + 0.114 * Color.blue(fg));
        return Math.abs(bgLum - fgLum) >= 100;
    }

    /**
     * Check if a color is dark
     */
    private boolean isDark(int color) {
        int lum = (int) (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color));
        return lum < 128;
    }

    /**
     * Get color from index (handles 16-color, 256-color, and RGB)
     */
    private int getColorFromIndex(int colorIndex) {
        if (colorIndex >= 0 && colorIndex < 256) {
            return colors[colorIndex];
        }
        return colors[7]; // Default to white (color 7) if index is invalid
    }

    private void applyPreviewContent(String content) {
        int cols = Math.max(10, (int) (getWidth() / charWidth));
        int rows = Math.max(5, (int) (getHeight() / charHeight));
        emulator = new TerminalEmulator(cols, rows);
        resetScrollback();
        emulator.setScrollbackListener(this::addScrollbackLine);
        emulator.write(content);
        invalidate();
    }

    private boolean isBracket(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }

    private boolean isHighRiskLine(int row, int cols) {
        if (emulator == null) return false;
        int baseRow = getDisplayBaseRow();
        int rows = getVisibleRowCount();
        ensureVisibleLineCache(baseRow, cols, rows);
        if (row >= 0 && row < visibleCacheRows) {
            Boolean cached = visibleHighRiskCache[row];
            if (cached != null) {
                return cached;
            }
        }
        String line = buildDisplayLine(row, cols).trim().toLowerCase(java.util.Locale.getDefault());
        boolean risky = !line.isEmpty()
            && (line.contains("rm -rf")
                || line.contains("mkfs")
                || line.contains("dd if=")
                || line.contains(":(){:|:&};:"));
        if (row >= 0 && row < visibleCacheRows) {
            visibleHighRiskCache[row] = risky;
        }
        return risky;
    }

    private void handleTwoFingerSelection(MotionEvent event, int action) {
        if (event.getPointerCount() < 2) {
            return;
        }
        float midX = (event.getX(0) + event.getX(1)) / 2f;
        float midY = (event.getY(0) + event.getY(1)) / 2f;
        int col = pointToColumn(midX);
        int row = pointToRow(midY);
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_DOWN) {
            selectionActive = true;
            selectionStartCol = col;
            selectionStartRow = row;
            selectionEndCol = col;
            selectionEndRow = row;
            invalidate();
            return;
        }
        if (action == MotionEvent.ACTION_MOVE && selectionActive) {
            selectionEndCol = col;
            selectionEndRow = row;
            invalidate();
        }
    }

    private void handleThreeFingerSwipe(MotionEvent event, int action) {
        if (event.getPointerCount() < 3) {
            return;
        }
        float avgX = (event.getX(0) + event.getX(1) + event.getX(2)) / 3f;
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_DOWN) {
            threeFingerActive = true;
            threeFingerTriggered = false;
            threeFingerStartX = avgX;
            return;
        }
        if (action == MotionEvent.ACTION_MOVE && threeFingerActive && !threeFingerTriggered) {
            float dx = avgX - threeFingerStartX;
            if (Math.abs(dx) >= threeFingerThresholdPx) {
                threeFingerTriggered = true;
                if (gestureListener != null) {
                    gestureListener.onThreeFingerSwipe(dx > 0 ? 1 : -1);
                }
            }
        }
    }

    private void requestCursorMove(float x, float y) {
        int col = pointToColumn(x);
        int row = pointToRow(y);
        if (col == lastRequestedCol && row == lastRequestedRow) {
            return;
        }
        lastRequestedCol = col;
        lastRequestedRow = row;
        if (gestureListener != null) {
            gestureListener.onCursorMoveRequest(col, row);
        }
    }

    private int pointToColumn(float x) {
        if (emulator == null || charWidth <= 0) return 0;
        float offset = getLineNumberOffset(emulator.getRows());
        int col = (int) ((x - offset) / charWidth);
        return Math.max(0, Math.min(emulator.getColumns() - 1, col));
    }

    private int pointToRow(float y) {
        if (emulator == null || charHeight <= 0) return 0;
        int row = (int) (y / charHeight);
        return Math.max(0, Math.min(emulator.getRows() - 1, row));
    }

    private float getLineNumberOffset(int rows) {
        if (!showLineNumbers || rows <= 0) return 0f;
        int digits = String.valueOf(rows).length();
        return (digits + 1) * charWidth;
    }

    private int getVisibleRowCount() {
        return emulator != null ? emulator.getRows() : 0;
    }

    private int getVisibleColumnCount() {
        return emulator != null ? emulator.getColumns() : 0;
    }

    private int getTotalRowCount() {
        return getScrollbackCount() + getVisibleRowCount();
    }

    private int getMaxScrollOffset() {
        int visible = getVisibleRowCount();
        int total = getTotalRowCount();
        return Math.max(0, total - visible);
    }

    private int getDisplayBaseRow() {
        int visible = getVisibleRowCount();
        int total = getTotalRowCount();
        int maxOffset = getMaxScrollOffset();
        if (scrollOffsetLines > maxOffset) scrollOffsetLines = maxOffset;
        if (scrollOffsetLines < 0) scrollOffsetLines = 0;
        return Math.max(0, total - visible - scrollOffsetLines);
    }

    private void setScrollOffset(int offset) {
        int max = getMaxScrollOffset();
        int clamped = Math.max(0, Math.min(offset, max));
        if (clamped != scrollOffsetLines) {
            scrollOffsetLines = clamped;
            invalidate();
            clearVisibleLineCache();
        }
    }

    private void scrollByPixels(float distanceY) {
        if (charHeight <= 0) return;
        int delta = Math.round(distanceY / charHeight);
        if (delta != 0) {
            setScrollOffset(scrollOffsetLines + delta);
        }
    }

    // 单指滚动与惯性处理
    private void handleSingleFingerScroll(MotionEvent event, int action) {
        if (charHeight <= 0) return;
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastScrollY = event.getY();
                isFlinging = false;
                if (!scroller.isFinished()) {
                    scroller.forceFinished(true);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float currentY = event.getY();
                float dy = lastScrollY - currentY;
                if (Math.abs(dy) > 0) {
                    scrollByPixels(dy);
                }
                lastScrollY = currentY;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                float velocityY = velocityTracker.getYVelocity();
                if (Math.abs(velocityY) > minFlingVelocity) {
                    startFling(-velocityY);
                }
                velocityTracker.recycle();
                velocityTracker = null;
                break;
            default:
                break;
        }
    }

    // 启动惯性滚动
    private void startFling(float velocityY) {
        if (charHeight <= 0) return;
        int maxOffset = getMaxScrollOffset();
        int maxPixels = Math.round(maxOffset * charHeight);
        int currentPixels = Math.round(scrollOffsetLines * charHeight);
        scroller.fling(
                0,
                currentPixels,
                0,
                Math.round(velocityY),
                0,
                0,
                0,
                maxPixels,
                FLING_OVERSCROLL_DISTANCE,
                FLING_OVERFLING_DISTANCE
        );
        isFlinging = true;
        postInvalidateOnAnimation();
    }

    // 计算并推进惯性滚动帧
    @Override
    public void computeScroll() {
        if (scroller == null) {
            return;
        }
        if (scroller.computeScrollOffset()) {
            if (charHeight > 0) {
                int offset = Math.round(scroller.getCurrY() / charHeight);
                setScrollOffset(offset);
            }
            postInvalidateOnAnimation();
        } else if (isFlinging) {
            isFlinging = false;
        }
    }

    private void addScrollbackLine(char[] chars, int[] styles) {
        if (chars == null || styles == null) return;
        if (maxScrollbackLines <= 0) return;
        ensureScrollbackCapacity(maxScrollbackLines);
        int newCount = Math.min(scrollbackCount + 1, maxScrollbackLines);
        if (scrollOffsetLines > 0) {
            int maxOffsetAfterAdd = Math.max(0, newCount - getVisibleRowCount());
            scrollOffsetLines = Math.min(scrollOffsetLines + 1, maxOffsetAfterAdd);
        }
        if (scrollbackCount < maxScrollbackLines) {
            int index = (scrollbackHead + scrollbackCount) % maxScrollbackLines;
            scrollbackBuffer[index] = new ScrollbackLine(chars, styles);
            scrollbackCount++;
        } else {
            scrollbackBuffer[scrollbackHead] = new ScrollbackLine(chars, styles);
            scrollbackHead = (scrollbackHead + 1) % maxScrollbackLines;
            if (scrollOffsetLines > 0) {
                scrollOffsetLines = Math.max(0, scrollOffsetLines - 1);
            }
        }
        clearVisibleLineCache();
    }

    private void resetScrollback() {
        scrollbackHead = 0;
        scrollbackCount = 0;
        if (maxScrollbackLines > 0) {
            scrollbackBuffer = new ScrollbackLine[maxScrollbackLines];
        } else {
            scrollbackBuffer = null;
        }
        scrollOffsetLines = 0;
        clearVisibleLineCache();
    }

    public void setMaxScrollbackLines(int maxLines) {
        int newMax = Math.max(0, maxLines);
        if (newMax == maxScrollbackLines) {
            return;
        }
        maxScrollbackLines = newMax;
        rebuildScrollbackBuffer();
        setScrollOffset(scrollOffsetLines);
        clearVisibleLineCache();
    }

    public void scrollToBottom() {
        setScrollOffset(0);
    }

    public boolean isAtBottom() {
        return scrollOffsetLines == 0;
    }

    private int decodeForegroundColor(int style) {
        return (style >> 8) & 0xFF;
    }

    private int decodeBackgroundColor(int style) {
        return style & 0xFF;
    }

    private String buildDisplayLine(int row, int cols) {
        if (emulator == null) return "";
        int baseRow = getDisplayBaseRow();
        ensureVisibleLineCache(baseRow, cols, getVisibleRowCount());
        if (row >= 0 && row < visibleCacheRows) {
            String cached = visibleLineCache[row];
            if (cached != null) return cached;
        }
        StringBuilder sb = new StringBuilder(cols);
        int globalRow = baseRow + row;
        int scrollbackCount = getScrollbackCount();
        if (globalRow < scrollbackCount) {
            ScrollbackLine line = getScrollbackLine(globalRow);
            if (line == null) {
                for (int col = 0; col < cols; col++) {
                    sb.append(' ');
                }
                String built = sb.toString();
                cacheVisibleLine(row, built);
                return built;
            }
            for (int col = 0; col < cols; col++) {
                char c = (col >= 0 && col < line.chars.length) ? line.chars[col] : ' ';
                if (c == 0) c = ' ';
                sb.append(c);
            }
            String built = sb.toString();
            cacheVisibleLine(row, built);
            return built;
        }
        TerminalEmulator.ScreenBuffer buffer = emulator.getScreenBuffer();
        if (buffer == null) return "";
        int bufferRow = globalRow - scrollbackCount;
        for (int col = 0; col < cols; col++) {
            char c = buffer.getChar(bufferRow, col);
            if (c == 0) c = ' ';
            sb.append(c);
        }
        String built = sb.toString();
        cacheVisibleLine(row, built);
        return built;
    }

    private void clearSelection() {
        if (selectionActive) {
            selectionActive = false;
            invalidate();
        }
    }

    private int getScrollbackCount() {
        return scrollbackCount;
    }

    private ScrollbackLine getScrollbackLine(int index) {
        if (scrollbackBuffer == null || index < 0 || index >= scrollbackCount) {
            return null;
        }
        int capacity = scrollbackBuffer.length;
        int actualIndex = (scrollbackHead + index) % capacity;
        return scrollbackBuffer[actualIndex];
    }

    private void ensureScrollbackCapacity(int capacity) {
        if (capacity <= 0) {
            scrollbackBuffer = null;
            scrollbackHead = 0;
            scrollbackCount = 0;
            return;
        }
        if (scrollbackBuffer == null || scrollbackBuffer.length != capacity) {
            rebuildScrollbackBuffer();
        }
    }

    private void rebuildScrollbackBuffer() {
        if (maxScrollbackLines <= 0) {
            scrollbackBuffer = null;
            scrollbackHead = 0;
            scrollbackCount = 0;
            clearVisibleLineCache();
            return;
        }
        ScrollbackLine[] newBuffer = new ScrollbackLine[maxScrollbackLines];
        int keep = Math.min(scrollbackCount, maxScrollbackLines);
        int start = Math.max(0, scrollbackCount - keep);
        for (int i = 0; i < keep; i++) {
            ScrollbackLine line = getScrollbackLine(start + i);
            newBuffer[i] = line;
        }
        scrollbackBuffer = newBuffer;
        scrollbackHead = 0;
        scrollbackCount = keep;
        scrollOffsetLines = Math.max(0, Math.min(scrollOffsetLines, getMaxScrollOffset()));
        clearVisibleLineCache();
    }

    private void ensureVisibleLineCache(int baseRow, int cols, int rows) {
        if (rows <= 0 || cols <= 0) {
            visibleLineCache = null;
            visibleHighRiskCache = null;
            visibleSearchMatchCache = null;
            visibleSearchMatchCount = null;
            visibleSearchQueryLower = null;
            visibleCacheBaseRow = -1;
            visibleCacheCols = -1;
            visibleCacheRows = -1;
            return;
        }
        if (visibleLineCache == null
                || visibleCacheBaseRow != baseRow
                || visibleCacheCols != cols
                || visibleCacheRows != rows) {
            visibleLineCache = new String[rows];
            visibleHighRiskCache = new Boolean[rows];
            visibleSearchMatchCache = new int[rows][];
            visibleSearchMatchCount = new int[rows];
            visibleSearchQueryLower = null;
            visibleCacheBaseRow = baseRow;
            visibleCacheCols = cols;
            visibleCacheRows = rows;
        }
    }

    private void cacheVisibleLine(int row, String line) {
        if (visibleLineCache == null) return;
        if (row < 0 || row >= visibleCacheRows) return;
        visibleLineCache[row] = line;
    }

    private void invalidateVisibleLineCache(int startRow, int endRow) {
        if (visibleLineCache == null) return;
        int start = Math.max(0, Math.min(visibleCacheRows - 1, startRow));
        int end = Math.max(0, Math.min(visibleCacheRows - 1, endRow));
        for (int i = start; i <= end; i++) {
            visibleLineCache[i] = null;
            if (visibleHighRiskCache != null) {
                visibleHighRiskCache[i] = null;
            }
            if (visibleSearchMatchCache != null) {
                visibleSearchMatchCache[i] = null;
            }
            if (visibleSearchMatchCount != null) {
                visibleSearchMatchCount[i] = 0;
            }
        }
    }

    private void clearVisibleLineCache() {
        visibleLineCache = null;
        visibleHighRiskCache = null;
        visibleSearchMatchCache = null;
        visibleSearchMatchCount = null;
        visibleSearchQueryLower = null;
        visibleCacheBaseRow = -1;
        visibleCacheCols = -1;
        visibleCacheRows = -1;
    }

    private void clearVisibleSearchCache() {
        visibleSearchQueryLower = null;
        if (visibleSearchMatchCache == null || visibleSearchMatchCount == null) return;
        for (int i = 0; i < visibleSearchMatchCache.length; i++) {
            visibleSearchMatchCache[i] = null;
            visibleSearchMatchCount[i] = 0;
        }
    }

    private void invalidateVisibleSearchCache(int startRow, int endRow) {
        if (visibleSearchMatchCache == null || visibleSearchMatchCount == null) return;
        int start = Math.max(0, Math.min(visibleCacheRows - 1, startRow));
        int end = Math.max(0, Math.min(visibleCacheRows - 1, endRow));
        for (int i = start; i <= end; i++) {
            visibleSearchMatchCache[i] = null;
            visibleSearchMatchCount[i] = 0;
        }
    }

    private int getSearchMatchesForRow(int row, int cols, String lowerSearch) {
        int baseRow = getDisplayBaseRow();
        int rows = getVisibleRowCount();
        ensureVisibleLineCache(baseRow, cols, rows);
        if (visibleSearchMatchCache == null || visibleSearchMatchCount == null) return 0;
        if (visibleSearchQueryLower == null || !visibleSearchQueryLower.equals(lowerSearch)) {
            visibleSearchQueryLower = lowerSearch;
            clearVisibleSearchCache();
        }
        if (row < 0 || row >= visibleCacheRows) return 0;
        if (visibleSearchMatchCache[row] != null) {
            return visibleSearchMatchCount[row];
        }
        String line = buildDisplayLine(row, cols);
        String lowerLine = line.toLowerCase(java.util.Locale.getDefault());
        int[] matches = new int[Math.max(1, line.length() / Math.max(1, lowerSearch.length()))];
        int count = 0;
        int startIndex = 0;
        while (startIndex < line.length()) {
            int matchIndex = lowerLine.indexOf(lowerSearch, startIndex);
            if (matchIndex == -1) break;
            if (count >= matches.length) {
                int[] expanded = new int[matches.length * 2];
                System.arraycopy(matches, 0, expanded, 0, matches.length);
                matches = expanded;
            }
            matches[count++] = matchIndex;
            startIndex = matchIndex + 1;
        }
        if (count == 0) {
            visibleSearchMatchCache[row] = new int[0];
            visibleSearchMatchCount[row] = 0;
            return 0;
        }
        int[] trimmed = new int[count];
        System.arraycopy(matches, 0, trimmed, 0, count);
        visibleSearchMatchCache[row] = trimmed;
        visibleSearchMatchCount[row] = count;
        return count;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // Getters and setters for public properties
    public TerminalEmulator getEmulator() {
        return emulator;
    }

    public void setCursorStyle(CursorStyle style) {
        this.cursorStyle = style;
        postInvalidate();
    }

    public void setCursorBlink(boolean blink) {
        this.cursorBlink = blink;
        if (blink) {
            cursorBlinkStart = System.currentTimeMillis();
        }
        postInvalidate();
    }

    public void setCursorColor(int color) {
        this.cursorColor = color;
        postInvalidate();
    }

    /**
     * Force cursor to be visible (disable blinking temporarily)
     */
    public void forceCursorVisible() {
        cursorBlinkStart = System.currentTimeMillis();
        postInvalidate();
    }

    public BackgroundScale getBackgroundScale() {
        return backgroundScale;
    }

    public int getBackgroundAlpha() {
        return backgroundAlpha;
    }

    public Bitmap getBackgroundImage() {
        return backgroundImage;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (gestureListener != null) {
            gestureListener.onTerminalKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }
}
