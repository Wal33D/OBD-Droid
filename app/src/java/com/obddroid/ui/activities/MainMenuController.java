package com.obddroid.ui.activities;

import android.view.Menu;
import android.view.MenuItem;

import com.obddroid.R;
import com.obddroid.obd.ObdProt;

/**
 * The main screen's options menu: which of connect/disconnect is shown, what each item does, and the
 * chart/dashboard actions of the data list's selection mode.
 * <p>
 * Actions go to a {@link Host} (MainActivity) and the service items are gated on the ECU state from
 * {@link MainConnectionState}, so the menu logic is unit tested without an Activity or a real Menu.
 */
final class MainMenuController {

    /** What the menu asks the activity to do. */
    interface Host {
        /** Back to the dashboard (home button and "Home"). */
        void goHome();

        /** Drop any connection and open the adapter picker (the connect icon). */
        void selectAdapter();

        /** Ask before disconnecting (the connected icon). */
        void confirmDisconnect();

        void openSettings();

        void showHelp();

        /** Switch the OBD service; the title goes in the toolbar. */
        void startService(int obdService, CharSequence title);

        /** A service item was picked before the ECU answered. */
        void warnEcuNotReady();
    }

    /** Shows or hides a menu item; the default goes to the attached Menu. */
    interface ItemVisibility {
        void setVisible(int itemId, boolean visible);
    }

    private final Host host;
    private final MainConnectionState connection;
    private final ItemVisibility visibility;
    private Menu menu;

    MainMenuController(Host host, MainConnectionState connection) {
        this.host = host;
        this.connection = connection;
        this.visibility = this::setItemVisible;
    }

    /** For tests: record visibility changes instead of touching a Menu. */
    MainMenuController(Host host, MainConnectionState connection, ItemVisibility visibility) {
        this.host = host;
        this.connection = connection;
        this.visibility = visibility;
    }

    /** Keep the inflated options menu (onCreateOptionsMenu). */
    void attach(Menu menu) {
        this.menu = menu;
    }

    /**
     * Show the green disconnect icon while connected, the connect icon otherwise.
     */
    void showConnected(boolean connected) {
        if (connected) {
            visibility.setVisible(R.id.secure_connect_scan, false);
            visibility.setVisible(R.id.disconnect, true);
        } else {
            visibility.setVisible(R.id.disconnect, false);
            visibility.setVisible(R.id.secure_connect_scan, true);
        }
    }

    /** Online, demo and saved-file modes all count as connected for the toolbar icon. */
    static boolean showsConnected(MainActivity.MODE mode) {
        return mode == MainActivity.MODE.ONLINE
                || mode == MainActivity.MODE.DEMO
                || mode == MainActivity.MODE.FILE;
    }

    /**
     * Bring the icons in line with the mode (onPrepareOptionsMenu), so the right one shows when
     * returning from another activity.
     */
    void onPrepare() {
        showConnected(showsConnected(connection.getMode()));
    }

    /**
     * Handle an options item.
     *
     * @param itemId the item's id
     * @param title  the item's title, used as toolbar title for service items
     * @return true when handled, false to let the activity's superclass have it
     */
    boolean onItemSelected(int itemId, CharSequence title) {
        if (itemId == android.R.id.home || itemId == R.id.service_home) {
            host.goHome();
        } else if (itemId == R.id.secure_connect_scan) {
            host.selectAdapter();
        } else if (itemId == R.id.disconnect) {
            host.confirmDisconnect();
        } else if (itemId == R.id.settings) {
            host.openSettings();
        } else if (itemId == R.id.service_none) {
            host.startService(ObdProt.OBD_SVC_NONE, title);
        } else if (itemId == R.id.service_data) {
            startWhenEcuReady(ObdProt.OBD_SVC_DATA, title);
        } else if (itemId == R.id.service_testcontrol) {
            startWhenEcuReady(ObdProt.OBD_SVC_CTRL_MODE, title);
        } else if (itemId == R.id.service_codes) {
            startWhenEcuReady(ObdProt.OBD_SVC_READ_CODES, title);
        } else if (itemId == R.id.action_info) {
            host.showHelp();
        } else {
            return false;
        }
        return true;
    }

    /**
     * The data view a selection-mode action switches to.
     *
     * @return the view mode, or null for an item that is not a view action
     */
    static MainActivity.DATA_VIEW_MODE viewModeForAction(int itemId) {
        if (itemId == R.id.chart_selected) {
            return MainActivity.DATA_VIEW_MODE.CHART;
        } else if (itemId == R.id.hud_selected) {
            return MainActivity.DATA_VIEW_MODE.HEADUP;
        } else if (itemId == R.id.dashboard_selected) {
            return MainActivity.DATA_VIEW_MODE.DASHBOARD;
        } else if (itemId == R.id.filter_selected) {
            return MainActivity.DATA_VIEW_MODE.FILTERED;
        }
        return null;
    }

    private void startWhenEcuReady(int obdService, CharSequence title) {
        if (connection.isEcuReady()) {
            host.startService(obdService, title);
        } else {
            host.warnEcuNotReady();
        }
    }

    private void setItemVisible(int itemId, boolean visible) {
        if (menu != null) {
            MenuItem item = menu.findItem(itemId);
            if (item != null) {
                item.setVisible(visible);
            }
        }
    }
}
