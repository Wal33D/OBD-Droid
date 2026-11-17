package com.obddroid.ui.activities;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AutoReconnectCountdownTest {

    @Test
    public void secondsRoundUp() {
        assertEquals(5, AutoReconnectCountdown.secondsLeft(5000));
        assertEquals(5, AutoReconnectCountdown.secondsLeft(4001));
        assertEquals(4, AutoReconnectCountdown.secondsLeft(4000));
        assertEquals(1, AutoReconnectCountdown.secondsLeft(1));
    }

    @Test
    public void progressRunsFromZeroToTheDuration() {
        assertEquals(0, AutoReconnectCountdown.progress(AutoReconnectCountdown.DURATION_MS));
        assertEquals(2500, AutoReconnectCountdown.progress(2500));
        assertEquals(AutoReconnectCountdown.DURATION_MS - 1, AutoReconnectCountdown.progress(1));
    }

    @Test
    public void messageNamesTheAdapterAndTheSeconds() {
        assertEquals("Auto-reconnecting to adapter in 5s...", AutoReconnectCountdown.message("adapter", 5));
        assertEquals("Auto-reconnecting to OBDII-BT in 3s...", AutoReconnectCountdown.message("OBDII-BT", 3));
    }
}
