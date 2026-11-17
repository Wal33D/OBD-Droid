package com.obddroid.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge safety net for targetSdk 35+.
 *
 * From Android 15 every window of an app targeting SDK 35 or higher is drawn edge to edge: the status and
 * navigation bars are transparent and lie over the app. AppCompat's decor already insets the content frame
 * on most screens (its root view fits system windows), but anything that still reaches
 * {@code android.R.id.content} would otherwise sit under the bars, the display cutout or the keyboard.
 * This pads the content frame of every activity by the insets that reach it, so no screen needs its own code.
 * Insets that AppCompat already consumed arrive as zero, so nothing is padded twice.
 */
public final class EdgeToEdgeInsets {

    private static final int BAR_TYPES =
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();

    private EdgeToEdgeInsets() {
    }

    /** Register for every activity of the app. A no-op before Android 15, where nothing is forced edge to edge. */
    public static void install(Application application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        application.registerActivityLifecycleCallbacks(new Callbacks());
    }

    /** Pad the activity's content frame by the system bar, cutout and (when resizing) keyboard insets. */
    public static void apply(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        final int baseLeft = content.getPaddingLeft();
        final int baseTop = content.getPaddingTop();
        final int baseRight = content.getPaddingRight();
        final int baseBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(BAR_TYPES);
            int bottom = bars.bottom;
            int softInputMode = activity.getWindow().getAttributes().softInputMode;
            if (padsForKeyboard(softInputMode)) {
                bottom = Math.max(bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            }
            view.setPadding(baseLeft + bars.left, baseTop + bars.top, baseRight + bars.right, baseBottom + bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    /**
     * Whether the keyboard height should be added to the bottom padding. Edge to edge, the system no longer
     * resizes the window for the keyboard, so this stands in for adjustResize. Windows that asked to pan or
     * to do nothing keep that behaviour.
     */
    static boolean padsForKeyboard(int softInputMode) {
        int adjust = softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
        return adjust != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                && adjust != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
    }

    private static final class Callbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            // After onCreate() returned, so setContentView() has installed AppCompat's content frame.
            apply(activity);
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }
}
