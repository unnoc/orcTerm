package com.orcterm.ui;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class TerminalThemeConfigUiTest {

    @Test
    public void opensThemeConfigDialog() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), TerminalActivity.class);
        intent.putExtra("theme_action", "editor");
        try (ActivityScenario<TerminalActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.theme_preview)).check(matches(isDisplayed()));
        }
    }
}
