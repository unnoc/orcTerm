package com.orcterm.ui;

import android.app.Application;
import android.graphics.Color;

import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ThemeRepositoryTest {

    private ThemeRepository repository;

    @Before
    public void setUp() {
        Application app = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        repository = new ThemeRepository(app);
    }

    @After
    public void tearDown() {
        repository.close();
    }

    @Test
    public void updatesConfigOnChanges() throws Exception {
        AtomicReference<ThemeRepository.ThemeConfig> latest = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Observer<ThemeRepository.ThemeConfig> observer = config -> {
            latest.set(config);
            latch.countDown();
        };
        repository.getTheme().observeForever(observer);

        repository.setFontSizeSp(18f);
        repository.setFontFamily(1);
        repository.setFontWeight(600);
        repository.setLineHeight(1.4f);
        repository.setLetterSpacing(0.6f);
        repository.setCursorStyle(2);
        repository.setCursorBlink(false);
        int bg = Color.rgb(10, 20, 30);
        int fg = Color.rgb(220, 230, 240);
        int hl = Color.rgb(12, 34, 56);
        repository.setBackgroundColor(bg);
        repository.setForegroundColor(fg);
        repository.setSearchHighlightColor(hl);
        repository.applyPreset("high_contrast");
        repository.refresh();

        latch.await(2, TimeUnit.SECONDS);
        ThemeRepository.ThemeConfig config = waitForConfig(latest, bg, fg, hl);
        repository.getTheme().removeObserver(observer);

        assertNotNull(config);
        assertTrue(config.fontSizeSp >= 17.5f && config.fontSizeSp <= 18.5f);
        assertEquals(1, config.fontFamily);
        assertEquals(600, config.fontWeight);
        assertTrue(Math.abs(config.lineHeight - 1.4f) < 0.15f);
        assertTrue(Math.abs(config.letterSpacing - 0.6f) < 0.15f);
        assertEquals(2, config.cursorStyle);
        assertEquals(false, config.cursorBlink);
        assertEquals(bg, config.backgroundColor);
        assertEquals(fg, config.foregroundColor);
        assertEquals(hl, config.searchHighlightColor);
        assertEquals("high_contrast", config.themePreset);
    }

    private ThemeRepository.ThemeConfig waitForConfig(AtomicReference<ThemeRepository.ThemeConfig> latest, int bg, int fg, int hl) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        ThemeRepository.ThemeConfig config = null;
        while (System.currentTimeMillis() < deadline) {
            config = latest.get();
            if (config != null
                && config.backgroundColor == bg
                && config.foregroundColor == fg
                && config.searchHighlightColor == hl
                && config.fontWeight == 600
                && config.cursorStyle == 2
                && "high_contrast".equals(config.themePreset)) {
                break;
            }
            Thread.sleep(50);
        }
        return config;
    }
}
