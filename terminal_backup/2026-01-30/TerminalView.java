package com.orcterm.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
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

    public void setOnResizeListener(OnResizeListener listener) {
        this.resizeListener = listener;
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

        initColors();

        updateMetrics();

        emulator = new TerminalEmulator(80, 24);

        setBackgroundColor(colors[0]);
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
    }

    public void append(String data) {
        if (emulator != null) {
            emulator.write(data);
            invalidateDirtyRegion();
        }
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

        // Draw search highlights
        if (searchQuery != null && !searchQuery.isEmpty()) {
            drawSearchHighlights(canvas);
        }

        // Draw cursor
        drawCursor(canvas);
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

        float y = 0;

        for (int row = 0; row < rows; row++) {
            float x = 0;
            
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
                
                // Only draw text for non-space characters
                if (c != ' ' && c != 0) {
                    int effectiveBg = actualBgColor == Color.TRANSPARENT ? colors[0] : actualBgColor;
                    if (!hasEnoughContrast(effectiveBg, actualFgColor)) {
                        actualFgColor = isDark(effectiveBg) ? Color.WHITE : Color.BLACK;
                    }
                    
                    Paint cachedPaint = getCachedPaint(actualFgColor);
                    charArray[0] = c;
                    // Adjust text position for proper baseline alignment
                    float textY = y + charHeight - (charHeight * 0.2f); // Better baseline positioning
                    canvas.drawText(charArray, 0, 1, x, textY, cachedPaint);
                }
                
                x += charWidth;
            }
            
            y += charHeight;
        }
    }

    /**
     * Draw search highlights
     */
    private void drawSearchHighlights(Canvas canvas) {
        if (searchQuery == null || searchQuery.isEmpty()) {
            return;
        }

        // Implementation would highlight search matches
        // This is a placeholder for search highlighting functionality
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

        float cursorX = emulator.getCursorX() * charWidth;
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
}