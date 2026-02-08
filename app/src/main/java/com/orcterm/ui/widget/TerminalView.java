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
import android.view.View;

import com.orcterm.core.terminal.TerminalEmulator;

/**
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

    // ANSI color table (0-15 for 16-color, 16-255 for 256-color)
    private int[] colors = new int[256];
    
    // Rendering optimization: avoid creating String objects per character
    private char[] charArray = new char[1];
    
    // Rendering optimization: Paint cache
    private java.util.Map<Integer, Paint> colorPaintCache = new java.util.HashMap<>();

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
        postInvalidate();
    }

    public void clearSearchQuery() {
        this.searchQuery = null;
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
        
        TerminalEmulator.ScreenBuffer buffer = emulator.getScreenBuffer();
        if (buffer == null) return 0;
        
        int rows = buffer.getRowCount();
        int cols = buffer.getColumnCount();
        String lowerQuery = query.toLowerCase(java.util.Locale.getDefault());
        int count = 0;
        
        for (int row = 0; row < rows; row++) {
            StringBuilder lineBuilder = new StringBuilder(cols);
            for (int col = 0; col < cols; col++) {
                char c = buffer.getChar(row, col);
                if (c == 0) c = ' '; // 空字符显示为空格
                lineBuilder.append(c);
            }
            String line = lineBuilder.toString();
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
        lineNumberPaint = new Paint(textPaint);
        lineNumberPaint.setColor(lineNumberColor);
        bracketMatchPaint = new Paint();
        bracketMatchPaint.setColor(bracketMatchColor);
        highRiskPaint = new Paint();
        highRiskPaint.setColor(highRiskHighlightColor);

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
                if (activePointerCount == 1 && !selectionActive) {
                    return true;
                }
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

        int rows = buffer.getRowCount();
        int cols = buffer.getColumnCount();

        int digits = showLineNumbers ? String.valueOf(rows).length() : 0;
        float lineNumberWidth = showLineNumbers ? (digits + 1) * charWidth : 0f;

        for (int row = 0; row < rows; row++) {
            float y = row * charHeight;
            float xOffset = lineNumberWidth;
            float baseline = y + charHeight - (charHeight * 0.2f);
            if (showLineNumbers) {
                lineNumberPaint.setColor(lineNumberColor);
                String num = String.format(java.util.Locale.getDefault(), "%" + digits + "d", row + 1);
                canvas.drawText(num, 0, num.length(), 0, baseline, lineNumberPaint);
            }
            if (showHighRiskHighlight && isHighRiskLine(buffer, row, cols)) {
                highRiskPaint.setColor(highRiskHighlightColor);
                canvas.drawRect(xOffset, y, xOffset + cols * charWidth, y + charHeight, highRiskPaint);
            }
            float x = xOffset;
            
            for (int col = 0; col < cols; col++) {
                char c = buffer.getChar(row, col);
                
                // Get character and its style information
                int fgColor = buffer.getForegroundColor(row, col);
                int bgColor = buffer.getBackgroundColor(row, col);
                
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
                    charArray[0] = c;
                    // Adjust text position for proper baseline alignment
                    canvas.drawText(charArray, 0, 1, x, baseline, cachedPaint);
                }
                
                x += charWidth;
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
        
        TerminalEmulator.ScreenBuffer buffer = emulator.getScreenBuffer();
        if (buffer == null) return;
        
        int rows = buffer.getRowCount();
        int cols = buffer.getColumnCount();
        String lowerSearch = searchQuery.toLowerCase(java.util.Locale.getDefault());
        float xOffset = getLineNumberOffset(rows);
        
        for (int row = 0; row < rows; row++) {
            // 获取整行文本
            StringBuilder lineBuilder = new StringBuilder(cols);
            for (int col = 0; col < cols; col++) {
                char c = buffer.getChar(row, col);
                if (c == 0) c = ' '; // 空字符显示为空格
                lineBuilder.append(c);
            }
            String line = lineBuilder.toString();
            String lowerLine = line.toLowerCase(java.util.Locale.getDefault());
            
            int startIndex = 0;
            while (startIndex < line.length()) {
                int matchIndex = lowerLine.indexOf(lowerSearch, startIndex);
                if (matchIndex == -1) break; // 没有找到更多匹配项
                
                // 计算匹配区域的坐标
                float left = xOffset + matchIndex * charWidth;
                float top = row * charHeight;
                float right = left + searchQuery.length() * charWidth;
                float bottom = top + charHeight;
                
                // 绘制搜索高亮
                Paint highlightPaint = new Paint();
                highlightPaint.setColor(searchHighlightColor);
                highlightPaint.setStyle(Paint.Style.FILL);
                canvas.drawRect(left, top, right, bottom, highlightPaint);
                
                startIndex = matchIndex + 1; // 移动到下一个可能的匹配位置
            }
        }
    }

    /**
     * Draw cursor
     */
    private void drawCursor(Canvas canvas) {
        if (emulator == null || !emulator.isCursorVisible()) return;

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
        // For now, invalidate the whole view
        // Could be optimized to only invalidate changed regions
        postInvalidate();
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
        emulator.write(content);
        invalidate();
    }

    private boolean isBracket(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }

    private boolean isHighRiskLine(TerminalEmulator.ScreenBuffer buffer, int row, int cols) {
        StringBuilder sb = new StringBuilder(cols);
        for (int col = 0; col < cols; col++) {
            char c = buffer.getChar(row, col);
            if (c == 0) c = ' ';
            sb.append(c);
        }
        String line = sb.toString().trim().toLowerCase(java.util.Locale.getDefault());
        if (line.isEmpty()) return false;
        return line.contains("rm -rf")
            || line.contains("mkfs")
            || line.contains("dd if=")
            || line.contains(":(){:|:&};:");
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

    private void clearSelection() {
        if (selectionActive) {
            selectionActive = false;
            invalidate();
        }
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
