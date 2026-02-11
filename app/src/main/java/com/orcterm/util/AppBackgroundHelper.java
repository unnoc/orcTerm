package com.orcterm.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;

/**
 * Apply app background image from prefs.
 */
public final class AppBackgroundHelper {
    private AppBackgroundHelper() {}

    public static boolean applyFromPrefs(Context context, View target) {
        if (context == null || target == null) return false;
        SharedPreferences prefs = context.getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
        String uriStr = prefs.getString("app_bg_uri", null);
        if (uriStr == null || uriStr.isEmpty()) return false;
        return applyUri(context, target, Uri.parse(uriStr));
    }

    public static boolean applyUri(Context context, View target, Uri uri) {
        if (context == null || target == null || uri == null) return false;
        try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            android.graphics.drawable.Drawable drawable = android.graphics.drawable.Drawable.createFromStream(in, uri.toString());
            if (drawable == null) return false;
            target.setBackground(drawable);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
