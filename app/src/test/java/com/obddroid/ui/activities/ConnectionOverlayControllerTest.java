package com.obddroid.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.obddroid.R;
import com.obddroid.ui.activities.ConnectionOverlayController.State;
import com.obddroid.ui.activities.ConnectionOverlayController.Step;
import com.obddroid.ui.activities.ConnectionOverlayController.Timeout;

import org.junit.Test;

public class ConnectionOverlayControllerTest {

    private static void assertShown(Step step, int title, int subtitle, Timeout timeout) {
        assertTrue(step.visible);
        assertEquals(title, step.titleRes);
        assertEquals(subtitle, step.subtitleRes);
        assertEquals(timeout, step.timeout);
    }

    private static void assertHidden(Step step) {
        assertFalse(step.visible);
        assertEquals("hiding always stops the timeout", Timeout.CANCEL, step.timeout);
    }

    @Test
    public void offlineAsksToConnectExceptInDemoMode() {
        assertShown(ConnectionOverlayController.plan(State.OFFLINE, false),
                R.string.connection_required_title, R.string.connection_required_message, Timeout.CANCEL);
        assertHidden(ConnectionOverlayController.plan(State.OFFLINE, true));
    }

    @Test
    public void connectingAndFailedShowWithoutTimeout() {
        assertShown(ConnectionOverlayController.plan(State.CONNECTING, false),
                R.string.connection_overlay_connecting_title,
                R.string.connection_overlay_connecting_subtitle, Timeout.CANCEL);
        assertShown(ConnectionOverlayController.plan(State.FAILED, false),
                R.string.connection_overlay_failed_title,
                R.string.connection_overlay_failed_subtitle, Timeout.CANCEL);
    }

    @Test
    public void waitingForTheVehicleStartsTheTimeout() {
        assertShown(ConnectionOverlayController.plan(State.DECODING, false),
                R.string.connection_overlay_decoding_title,
                R.string.connection_overlay_decoding_subtitle, Timeout.START);
        assertShown(ConnectionOverlayController.plan(State.FINALIZING, false),
                R.string.connection_overlay_finalizing_title,
                R.string.connection_overlay_finalizing_subtitle, Timeout.START);
    }

    @Test
    public void readyHides() {
        assertHidden(ConnectionOverlayController.plan(State.READY, false));
        assertHidden(ConnectionOverlayController.plan(State.READY, true));
    }

    @Test
    public void demoModeOnlyChangesOffline() {
        for (State state : State.values()) {
            if (state == State.OFFLINE) {
                continue;
            }
            Step live = ConnectionOverlayController.plan(state, false);
            Step demo = ConnectionOverlayController.plan(state, true);
            assertEquals(state.name(), live.visible, demo.visible);
            assertEquals(state.name(), live.titleRes, demo.titleRes);
            assertEquals(state.name(), live.timeout, demo.timeout);
        }
    }

    @Test
    public void onlyDecodingAndFinalizingWaitForTheVehicle() {
        for (State state : State.values()) {
            boolean expected = state == State.DECODING || state == State.FINALIZING;
            assertEquals(state.name(), expected, ConnectionOverlayController.isWaitingForVehicle(state));
        }
    }

    @Test
    public void decodedVehicleIsReadyOnceTheFooterHasIt() {
        assertEquals(State.READY, ConnectionOverlayController.afterVehicleDecoded(true));
        assertEquals(State.FINALIZING, ConnectionOverlayController.afterVehicleDecoded(false));
    }
}
