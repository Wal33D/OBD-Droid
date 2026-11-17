package com.obddroid.ui.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.obddroid.R;
import com.obddroid.utils.SnackbarHelper;

import java.util.logging.Logger;

/**
 * The bar that counts down before the startup auto-reconnect, with a cancel button.
 * <p>
 * Tapping the bar or its button cancels; when the countdown runs out the reconnect callback runs.
 */
final class AutoReconnectCountdown {

    /** Supplies the name shown in the countdown text. */
    interface AdapterName {
        String get();
    }

    static final int DURATION_MS = 5000;
    /** Progress and text refresh, every 100 ms for a smooth bar. */
    static final int UPDATE_INTERVAL_MS = 100;
    private static final int START_SECONDS = 5;

    private final Activity activity;
    private final Logger log;
    private final AdapterName adapterName;
    private final Runnable reconnect;

    private View bar;
    private ProgressBar progressBar;
    private TextView text;
    private Handler handler;
    private Runnable tick;
    private int secondsRemaining = START_SECONDS;

    AutoReconnectCountdown(Activity activity, Logger log, AdapterName adapterName, Runnable reconnect) {
        this.activity = activity;
        this.log = log;
        this.adapterName = adapterName;
        this.reconnect = reconnect;
    }

    /** Whole seconds left, rounded up, for the countdown text. */
    static int secondsLeft(long remainingMs) {
        return (int) Math.ceil(remainingMs / 1000.0);
    }

    /** Progress bar position (0 .. DURATION_MS) for the time left. */
    static int progress(long remainingMs) {
        return (int) (DURATION_MS - remainingMs);
    }

    @SuppressLint("DefaultLocale") // same formatting as before the split
    static String message(String adapterName, int seconds) {
        return String.format("Auto-reconnecting to %s in %ds...", adapterName, seconds);
    }

    /** Find the bar in the activity's layout and wire its cancel handlers. */
    void bind() {
        bar = activity.findViewById(R.id.auto_reconnect_countdown_bar);
        if (bar == null) {
            log.warning("Countdown bar not found in layout");
            return;
        }

        // Find child views
        progressBar = bar.findViewById(R.id.countdown_progress);
        text = bar.findViewById(R.id.countdown_text);
        View cancelBtn = bar.findViewById(R.id.countdown_cancel_btn);

        handler = new Handler(Looper.getMainLooper());

        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> cancel());
        }

        // Clicking anywhere on the bar also cancels
        bar.setOnClickListener(v -> cancel());

        log.info("Auto-reconnect countdown bar initialized");
    }

    /** Show the bar and start counting down; without a bar, reconnect at once. */
    void show() {
        if (bar == null) {
            log.warning("Countdown bar not initialized, falling back to direct reconnect");
            reconnect.run();
            return;
        }

        String name = adapterName.get();

        // Reset countdown
        secondsRemaining = START_SECONDS;

        if (text != null) {
            text.setText(message(name, secondsRemaining));
        }
        if (progressBar != null) {
            progressBar.setProgress(0);
        }

        bar.setVisibility(View.VISIBLE);

        log.info("Showing auto-reconnect countdown for adapter: " + name);

        start();
    }

    /** Stop the countdown, hide the bar and tell the user. */
    void cancel() {
        log.info("Auto-reconnect cancelled by user");

        if (handler != null && tick != null) {
            handler.removeCallbacks(tick);
        }
        if (bar != null) {
            bar.setVisibility(View.GONE);
        }

        SnackbarHelper.showInfo(activity, "Auto-reconnect cancelled");
    }

    private void start() {
        final long endTime = System.currentTimeMillis() + DURATION_MS;

        tick = new Runnable() {
            @Override
            public void run() {
                long remaining = endTime - System.currentTimeMillis();

                if (remaining <= 0) {
                    // Countdown complete - trigger reconnect
                    if (bar != null) {
                        bar.setVisibility(View.GONE);
                    }
                    log.info("Auto-reconnect countdown complete, initiating connection");
                    reconnect.run();
                } else {
                    if (progressBar != null) {
                        progressBar.setProgress(progress(remaining));
                    }

                    int seconds = secondsLeft(remaining);
                    if (seconds != secondsRemaining) {
                        secondsRemaining = seconds;
                        if (text != null) {
                            text.setText(message(adapterName.get(), seconds));
                        }
                    }

                    if (handler != null) {
                        handler.postDelayed(this, UPDATE_INTERVAL_MS);
                    }
                }
            }
        };

        handler.post(tick);
    }
}
