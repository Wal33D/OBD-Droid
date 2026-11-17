package com.obddroid.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;
import com.obddroid.ui.activities.MainConnectionState.AutoReconnect;
import com.obddroid.ui.activities.MainConnectionState.NrcAction;
import com.obddroid.ui.activities.MainConnectionState.ReconnectPlan;

import org.junit.Test;

public class MainConnectionStateTest {

    @Test
    public void startsOfflineWithNoEcu() {
        MainConnectionState state = new MainConnectionState();
        assertEquals(MainActivity.MODE.OFFLINE, state.getMode());
        assertEquals(ElmProt.STAT.UNDEFINED, state.getEcuConnectionState());
        assertFalse(state.isEcuUserSelected());
        assertFalse(state.isEcuReady());
        assertNull(state.serviceStatus());
    }

    @Test
    public void ecuIsReadyOnlyWhenDetectedOrConnected() {
        MainConnectionState state = new MainConnectionState();
        for (ElmProt.STAT stat : ElmProt.STAT.values()) {
            state.setEcuConnectionState(stat);
            boolean expected = stat == ElmProt.STAT.ECU_DETECTED || stat == ElmProt.STAT.CONNECTED;
            assertEquals(stat.name(), expected, state.isEcuReady());
        }
    }

    @Test
    public void protocolStateIsTrackedAndShown() {
        MainConnectionState state = new MainConnectionState();
        state.setMode(MainActivity.MODE.ONLINE);
        assertEquals(ElmProt.STAT.CONNECTING, state.onProtocolState(ElmProt.STAT.CONNECTING, false));
        assertEquals(ElmProt.STAT.CONNECTING, state.getEcuConnectionState());
        assertEquals(ElmProt.STAT.ECU_DETECTED, state.onProtocolState(ElmProt.STAT.ECU_DETECTED, false));
        assertEquals(ElmProt.STAT.NODATA, state.onProtocolState(ElmProt.STAT.NODATA, false));
    }

    @Test
    public void faultCodeViewKeepsItsStatusThroughNoDataAndConnecting() {
        MainConnectionState state = new MainConnectionState();
        state.setMode(MainActivity.MODE.ONLINE);
        assertNull(state.onProtocolState(ElmProt.STAT.NODATA, true));
        assertEquals("state is still tracked", ElmProt.STAT.NODATA, state.getEcuConnectionState());
        assertNull(state.onProtocolState(ElmProt.STAT.CONNECTING, true));
        assertEquals(ElmProt.STAT.CONNECTED, state.onProtocolState(ElmProt.STAT.CONNECTED, true));
    }

    @Test
    public void demoModeTracksStateButShowsNothing() {
        MainConnectionState state = new MainConnectionState();
        state.setMode(MainActivity.MODE.DEMO);
        assertNull(state.onProtocolState(ElmProt.STAT.CONNECTED, false));
        assertEquals(ElmProt.STAT.CONNECTED, state.getEcuConnectionState());
    }

    @Test
    public void selectedEcuIsNotOverwrittenByConnectedOrDetected() {
        MainConnectionState state = new MainConnectionState();
        state.setMode(MainActivity.MODE.ONLINE);
        state.setEcuUserSelected(true);
        assertNull(state.onProtocolState(ElmProt.STAT.CONNECTED, false));
        assertEquals(ElmProt.STAT.ECU_SELECTED, state.onProtocolState(ElmProt.STAT.ECU_DETECTED, false));
        assertEquals("other states still show", ElmProt.STAT.NODATA,
                state.onProtocolState(ElmProt.STAT.NODATA, false));
    }

    @Test
    public void serviceStatusPrefersSelectionThenDetectedOrConnected() {
        MainConnectionState state = new MainConnectionState();
        state.setEcuConnectionState(ElmProt.STAT.NODATA);
        assertNull(state.serviceStatus());
        state.setEcuConnectionState(ElmProt.STAT.CONNECTED);
        assertEquals(ElmProt.STAT.CONNECTED, state.serviceStatus());
        state.setEcuConnectionState(ElmProt.STAT.ECU_DETECTED);
        assertEquals(ElmProt.STAT.ECU_DETECTED, state.serviceStatus());
        state.setEcuUserSelected(true);
        state.setEcuConnectionState(ElmProt.STAT.NODATA);
        assertEquals(ElmProt.STAT.ECU_SELECTED, state.serviceStatus());
    }

    @Test
    public void resetEcuForgetsStateAndSelection() {
        MainConnectionState state = new MainConnectionState();
        state.setEcuConnectionState(ElmProt.STAT.CONNECTED);
        state.setEcuUserSelected(true);
        state.resetEcu();
        assertEquals(ElmProt.STAT.UNDEFINED, state.getEcuConnectionState());
        assertFalse(state.isEcuUserSelected());
    }

    @Test
    public void reconnectCooldownRoundsUpAndEndsAfterFifteenSeconds() {
        MainConnectionState state = new MainConnectionState();
        long t0 = 1_000_000L;
        assertEquals("first reconnect is allowed", 0, state.reconnectCooldownSecondsLeft(t0));
        state.markReconnectAttempt(t0);
        assertEquals(t0, state.getLastReconnectTime());
        assertEquals(16, state.reconnectCooldownSecondsLeft(t0));
        assertEquals(15, state.reconnectCooldownSecondsLeft(t0 + 1));
        assertEquals(1, state.reconnectCooldownSecondsLeft(t0 + 14_999));
        assertEquals(0, state.reconnectCooldownSecondsLeft(t0 + MainConnectionState.RECONNECT_COOLDOWN_MS));
    }

    @Test
    public void onlyOneDisconnectAtATime() {
        MainConnectionState state = new MainConnectionState();
        assertTrue(state.beginDisconnect());
        assertFalse(state.beginDisconnect());
        state.endDisconnect();
        assertTrue(state.beginDisconnect());
    }

    @Test
    public void autoReconnectIsCheckedOncePerSession() {
        MainConnectionState state = new MainConnectionState();
        assertEquals(AutoReconnect.DISABLED, state.checkAutoReconnect(false, "BLUETOOTH", false, null));
        assertEquals(AutoReconnect.ALREADY_CHECKED, state.checkAutoReconnect(true, "BLUETOOTH", false, null));
    }

    @Test
    public void autoReconnectOutcomes() {
        assertEquals(AutoReconnect.NO_ADAPTER,
                new MainConnectionState().checkAutoReconnect(true, null, false, null));
        assertEquals(AutoReconnect.ALREADY_CONNECTED,
                new MainConnectionState().checkAutoReconnect(true, "USB", true, null));
        for (ElmProt.STAT up : new ElmProt.STAT[]{
                ElmProt.STAT.CONNECTED, ElmProt.STAT.ECU_DETECTED, ElmProt.STAT.ECU_SELECTED}) {
            assertEquals(up.name(), AutoReconnect.ALREADY_CONNECTED,
                    new MainConnectionState().checkAutoReconnect(true, "USB", false, up));
        }
        MainConnectionState demo = new MainConnectionState();
        demo.setMode(MainActivity.MODE.DEMO);
        assertEquals(AutoReconnect.NOT_OFFLINE, demo.checkAutoReconnect(true, "NETWORK", false, ElmProt.STAT.NODATA));
        assertEquals(AutoReconnect.COUNTDOWN,
                new MainConnectionState().checkAutoReconnect(true, "NETWORK", false, ElmProt.STAT.DISCONNECTED));
    }

    @Test
    public void elmLinkUpStates() {
        assertTrue(MainConnectionState.isElmLinkUp(ElmProt.STAT.CONNECTED));
        assertTrue(MainConnectionState.isElmLinkUp(ElmProt.STAT.ECU_DETECTED));
        assertTrue(MainConnectionState.isElmLinkUp(ElmProt.STAT.ECU_SELECTED));
        assertFalse(MainConnectionState.isElmLinkUp(ElmProt.STAT.CONNECTING));
        assertFalse(MainConnectionState.isElmLinkUp(null));
    }

    @Test
    public void reconnectPlanFromSavedPreferences() {
        assertEquals(ReconnectPlan.NO_ADAPTER, MainConnectionState.planReconnect(null, "AA:BB", "10.0.0.1"));
        assertEquals(ReconnectPlan.BLUETOOTH, MainConnectionState.planReconnect("BLUETOOTH", "AA:BB", null));
        assertEquals(ReconnectPlan.BLUETOOTH_NO_ADDRESS, MainConnectionState.planReconnect("BLUETOOTH", null, "10.0.0.1"));
        assertEquals(ReconnectPlan.NETWORK, MainConnectionState.planReconnect("NETWORK", null, "10.0.0.1"));
        assertEquals(ReconnectPlan.NETWORK_NO_ADDRESS, MainConnectionState.planReconnect("NETWORK", "AA:BB", null));
        assertEquals(ReconnectPlan.USB_MANUAL, MainConnectionState.planReconnect("USB", "AA:BB", "10.0.0.1"));
        assertEquals(ReconnectPlan.UNKNOWN_TYPE, MainConnectionState.planReconnect("SERIAL", "AA:BB", "10.0.0.1"));
    }

    @Test
    public void adapterDisplayNameUsesSavedNameForBluetoothAndUsbOnly() {
        assertEquals("OBDII-BT", MainConnectionState.adapterDisplayName("BLUETOOTH", "OBDII-BT", "usb0"));
        assertEquals("usb0", MainConnectionState.adapterDisplayName("USB", "OBDII-BT", "usb0"));
        assertEquals("adapter", MainConnectionState.adapterDisplayName("NETWORK", "OBDII-BT", "usb0"));
        assertEquals("adapter", MainConnectionState.adapterDisplayName("BLUETOOTH", "", "usb0"));
        assertEquals("adapter", MainConnectionState.adapterDisplayName("USB", "OBDII-BT", null));
        assertEquals("adapter", MainConnectionState.adapterDisplayName(null, "OBDII-BT", "usb0"));
    }

    @Test
    public void notSupportedNrcDependsOnTheService() {
        assertEquals(NrcAction.VIN_UNSUPPORTED, MainConnectionState.nrcAction(0x12, ObdProt.OBD_SVC_VEH_INFO));
        assertEquals(NrcAction.TEST_CONTROL_UNSUPPORTED, MainConnectionState.nrcAction(0x12, ObdProt.OBD_SVC_CTRL_MODE));
        assertEquals(NrcAction.IGNORE, MainConnectionState.nrcAction(0x12, ObdProt.OBD_SVC_DATA));
        assertEquals(NrcAction.SHOW, MainConnectionState.nrcAction(0x12, ObdProt.OBD_SVC_READ_CODES));
    }

    @Test
    public void otherNrcsAreShown() {
        assertEquals(NrcAction.SHOW, MainConnectionState.nrcAction(0x11, ObdProt.OBD_SVC_VEH_INFO));
        assertEquals(NrcAction.SHOW, MainConnectionState.nrcAction(0x31, ObdProt.OBD_SVC_DATA));
    }
}
