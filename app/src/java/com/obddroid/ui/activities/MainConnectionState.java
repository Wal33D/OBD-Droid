package com.obddroid.ui.activities;

import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connection state of the main screen, kept apart from the activity's views.
 * <p>
 * Holds the operating mode, the ECU state reported by the protocol, and the reconnect and disconnect
 * guards, and answers the questions MainActivity asks about them (which status to show, whether a
 * reconnect may start, what to do with a negative response). It has no Android dependencies, so the
 * decisions are unit tested; MainActivity keeps the side effects (CommService, VehicleManager, views).
 */
final class MainConnectionState {

    /** Minimum time between two manual reconnect attempts. */
    static final int RECONNECT_COOLDOWN_MS = 15000;

    /** Adapter types stored under LAST_ADAPTER_TYPE. */
    static final String ADAPTER_BLUETOOTH = "BLUETOOTH";
    static final String ADAPTER_NETWORK = "NETWORK";
    static final String ADAPTER_USB = "USB";

    /** NRC 0x12: sub-function not supported / feature not available. */
    static final int NRC_NOT_SUPPORTED = 0x12;

    /** What a reconnect to the last adapter can do with the saved preferences. */
    enum ReconnectPlan {
        /** No adapter has been used yet. */
        NO_ADAPTER,
        BLUETOOTH,
        BLUETOOTH_NO_ADDRESS,
        NETWORK,
        NETWORK_NO_ADDRESS,
        /** USB needs the user to pick the device again. */
        USB_MANUAL,
        UNKNOWN_TYPE
    }

    /** Outcome of the once-per-session auto-reconnect check. */
    enum AutoReconnect {
        /** Already checked in this session: do nothing. */
        ALREADY_CHECKED,
        DISABLED,
        NO_ADAPTER,
        ALREADY_CONNECTED,
        NOT_OFFLINE,
        /** Show the countdown and reconnect when it runs out. */
        COUNTDOWN
    }

    /** How to handle a negative response code from the protocol. */
    enum NrcAction {
        /** Mode 09 is not supported: report the VIN as missing and leave the service. */
        VIN_UNSUPPORTED,
        /** Mode 08 is not supported: tell the user and leave the service. */
        TEST_CONTROL_UNSUPPORTED,
        /** A single unsupported PID in mode 01: normal, say nothing. */
        IGNORE,
        /** Show the NRC message as the protocol classified it. */
        SHOW
    }

    private MainActivity.MODE mode = MainActivity.MODE.OFFLINE;
    private ElmProt.STAT ecuConnectionState = ElmProt.STAT.UNDEFINED;
    private boolean ecuUserSelected = false;
    private boolean manuallyReconnecting = false;
    private boolean autoReconnectChecked = false;
    private long lastReconnectTime = 0;
    private final AtomicBoolean disconnecting = new AtomicBoolean(false);

    MainActivity.MODE getMode() {
        return mode;
    }

    void setMode(MainActivity.MODE mode) {
        this.mode = mode;
    }

    ElmProt.STAT getEcuConnectionState() {
        return ecuConnectionState;
    }

    void setEcuConnectionState(ElmProt.STAT state) {
        ecuConnectionState = state;
    }

    boolean isEcuUserSelected() {
        return ecuUserSelected;
    }

    void setEcuUserSelected(boolean selected) {
        ecuUserSelected = selected;
    }

    /** Forget the ECU: back to UNDEFINED and not selected (new connection, disconnect). */
    void resetEcu() {
        ecuConnectionState = ElmProt.STAT.UNDEFINED;
        ecuUserSelected = false;
    }

    /** True once the ECU answered, which is when the per-service menu items may start a service. */
    boolean isEcuReady() {
        return ecuConnectionState == ElmProt.STAT.ECU_DETECTED
                || ecuConnectionState == ElmProt.STAT.CONNECTED;
    }

    /**
     * Record a protocol state change and say which state the toolbar subtitle should show.
     *
     * @param state             new protocol state
     * @param viewingFaultCodes true while the fault code list is shown
     * @return the state to show, or null to keep the current subtitle
     */
    ElmProt.STAT onProtocolState(ElmProt.STAT state, boolean viewingFaultCodes) {
        // Reading codes and background polling produce NODATA/CONNECTING; keep the last good status there.
        boolean skip = viewingFaultCodes
                && (state == ElmProt.STAT.NODATA || state == ElmProt.STAT.CONNECTING);

        ecuConnectionState = state;

        // The ELM status is not shown in demo mode.
        if (mode == MainActivity.MODE.DEMO || skip) {
            return null;
        }
        // Don't overwrite "ECU selected" when the state goes to CONNECTED.
        if (ecuUserSelected && state == ElmProt.STAT.CONNECTED) {
            return null;
        }
        if (ecuUserSelected && state == ElmProt.STAT.ECU_DETECTED) {
            return ElmProt.STAT.ECU_SELECTED;
        }
        return state;
    }

    /**
     * The ECU state to show after switching service (dashboard, fault codes, after clearing codes).
     *
     * @return ECU_SELECTED when the ECU was selected, the current state when it is CONNECTED or
     *         ECU_DETECTED, otherwise null (leave the subtitle to the mode)
     */
    ElmProt.STAT serviceStatus() {
        if (ecuUserSelected) {
            return ElmProt.STAT.ECU_SELECTED;
        }
        if (ecuConnectionState == ElmProt.STAT.CONNECTED
                || ecuConnectionState == ElmProt.STAT.ECU_DETECTED) {
            return ecuConnectionState;
        }
        return null;
    }

    boolean isManuallyReconnecting() {
        return manuallyReconnecting;
    }

    void setManuallyReconnecting(boolean reconnecting) {
        manuallyReconnecting = reconnecting;
    }

    long getLastReconnectTime() {
        return lastReconnectTime;
    }

    /**
     * Seconds the user still has to wait before another manual reconnect.
     *
     * @param now current time in milliseconds
     * @return 0 when a reconnect may start, otherwise the seconds left (rounded up, at least 1)
     */
    long reconnectCooldownSecondsLeft(long now) {
        long sinceLast = now - lastReconnectTime;
        if (sinceLast < RECONNECT_COOLDOWN_MS) {
            return (RECONNECT_COOLDOWN_MS - sinceLast) / 1000 + 1;
        }
        return 0;
    }

    /** Remember that a manual reconnect started now, which starts the cooldown. */
    void markReconnectAttempt(long now) {
        lastReconnectTime = now;
    }

    /**
     * Claim the disconnect. Only the first caller gets true until {@link #endDisconnect()}.
     */
    boolean beginDisconnect() {
        return disconnecting.compareAndSet(false, true);
    }

    void endDisconnect() {
        disconnecting.set(false);
    }

    /**
     * Decide the once-per-session auto-reconnect. Every outcome but ALREADY_CHECKED marks the check as done.
     *
     * @param enabled          the auto_reconnect_on_startup preference
     * @param lastAdapterType  LAST_ADAPTER_TYPE preference, or null
     * @param commConnected    true when the activity's CommService reports CONNECTED
     * @param elmStatus        CommService.elm status, or null when there is no protocol instance
     */
    AutoReconnect checkAutoReconnect(boolean enabled, String lastAdapterType,
                                     boolean commConnected, ElmProt.STAT elmStatus) {
        if (autoReconnectChecked) {
            return AutoReconnect.ALREADY_CHECKED;
        }
        autoReconnectChecked = true;
        if (!enabled) {
            return AutoReconnect.DISABLED;
        }
        if (lastAdapterType == null) {
            return AutoReconnect.NO_ADAPTER;
        }
        // After the activity is recreated the CommService reference can be gone while the link is up,
        // so the protocol status counts too.
        if (commConnected || isElmLinkUp(elmStatus)) {
            return AutoReconnect.ALREADY_CONNECTED;
        }
        if (mode != MainActivity.MODE.OFFLINE) {
            return AutoReconnect.NOT_OFFLINE;
        }
        return AutoReconnect.COUNTDOWN;
    }

    /** True for the protocol states in which the adapter link counts as up. */
    static boolean isElmLinkUp(ElmProt.STAT elmStatus) {
        return elmStatus == ElmProt.STAT.CONNECTED
                || elmStatus == ElmProt.STAT.ECU_DETECTED
                || elmStatus == ElmProt.STAT.ECU_SELECTED;
    }

    /**
     * What reconnecting to the last adapter can do with the saved preferences.
     *
     * @param lastAdapterType LAST_ADAPTER_TYPE, or null
     * @param btAddress       LAST_DEV_ADDRESS, or null
     * @param networkIp       DEVICE_ADDRESS, or null
     */
    static ReconnectPlan planReconnect(String lastAdapterType, String btAddress, String networkIp) {
        if (lastAdapterType == null) {
            return ReconnectPlan.NO_ADAPTER;
        }
        switch (lastAdapterType) {
            case ADAPTER_BLUETOOTH:
                return btAddress != null ? ReconnectPlan.BLUETOOTH : ReconnectPlan.BLUETOOTH_NO_ADDRESS;
            case ADAPTER_NETWORK:
                return networkIp != null ? ReconnectPlan.NETWORK : ReconnectPlan.NETWORK_NO_ADDRESS;
            case ADAPTER_USB:
                return ReconnectPlan.USB_MANUAL;
            default:
                return ReconnectPlan.UNKNOWN_TYPE;
        }
    }

    /**
     * Name of the last adapter for the auto-reconnect countdown: the saved device name for Bluetooth
     * and USB, otherwise "adapter".
     */
    static String adapterDisplayName(String lastAdapterType, String btDeviceName, String usbDeviceName) {
        String name = null;
        if (ADAPTER_BLUETOOTH.equals(lastAdapterType)) {
            name = btDeviceName;
        } else if (ADAPTER_USB.equals(lastAdapterType)) {
            name = usbDeviceName;
        }
        return (name != null && !name.isEmpty()) ? name : "adapter";
    }

    /**
     * How to handle a negative response.
     *
     * @param nrcCode    the response code
     * @param obdService the service the protocol is running
     */
    static NrcAction nrcAction(int nrcCode, int obdService) {
        if (nrcCode == NRC_NOT_SUPPORTED) {
            switch (obdService) {
                case ObdProt.OBD_SVC_VEH_INFO:
                    return NrcAction.VIN_UNSUPPORTED;
                case ObdProt.OBD_SVC_CTRL_MODE:
                    return NrcAction.TEST_CONTROL_UNSUPPORTED;
                case ObdProt.OBD_SVC_DATA:
                    return NrcAction.IGNORE;
                default:
                    break;
            }
        }
        return NrcAction.SHOW;
    }
}
