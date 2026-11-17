package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;
import com.obddroid.utils.SnackbarHelper;

import java.util.logging.Logger;

/**
 * Helper utilities for configuring the dashboard cards shown on the main screen.
 */
final class DashboardUiHelper {
    private static final Logger log = Logger.getLogger(DashboardUiHelper.class.getName());

    private DashboardUiHelper() {
        // Utility class
    }

    static void setupDashboardCards(MainActivity activity) {
        for (DashboardCards.Card card : DashboardCards.ALL) {
            View view = activity.findViewById(card.viewId);
            if (view == null) {
                log.warning(card.logName + " card NOT found!");
                continue;
            }
            addCardPressAnimation(view);
            view.setOnClickListener(v -> {
                log.info(card.logName + " card clicked!");
                if (card.target != null) {
                    log.info("Launching " + card.target.getSimpleName());
                    activity.startActivity(new Intent(activity, card.target));
                } else {
                    startTestControl(activity);
                }
            });
            view.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                card.title != null ? card.title : activity.getString(card.titleRes),
                card.info
            ));
        }
    }

    /**
     * Test Control card: switch the main screen to OBD service 08 once the ECU answered.
     */
    private static void startTestControl(MainActivity activity) {
        ElmProt.STAT ecuState = activity.getEcuConnectionState();
        if (DashboardCards.testControlAllowed(ecuState)) {
            UnsupportedModeHelper helper = activity.getUnsupportedModeHelper();
            if (helper != null) {
                helper.recordPreUnsupportedState(ecuState);
            }
            activity.setObdService(ObdProt.OBD_SVC_CTRL_MODE, DashboardCards.TEST_CONTROL_TITLE);
        } else {
            SnackbarHelper.showWarning(activity, "Please connect to vehicle first");
        }
    }

    private static void addCardPressAnimation(View card) {
        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(100)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                    break;
            }
            return false;
        });
    }

    private static boolean showCardInfoDialog(MainActivity activity, String title, String message) {
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show();
        return true;
    }
}
