package com.obddroid.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;

import com.obddroid.R;
import com.obddroid.features.copilot.ui.CoPilotActivity;
import com.obddroid.features.emissions.ui.EmissionsActivity;
import com.obddroid.features.fueleconomy.ui.FuelEconomyActivity;
import com.obddroid.features.recalls.ui.RecallActivity;
import com.obddroid.features.trackmode.TrackModeActivity;
import com.obddroid.features.vehiclehistory.ui.AutoCheckActivity;
import com.obddroid.obd.ElmProt;

import org.junit.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DashboardCardsTest {

    private static DashboardCards.Card card(int viewId) {
        for (DashboardCards.Card card : DashboardCards.ALL) {
            if (card.viewId == viewId) {
                return card;
            }
        }
        throw new AssertionError("no card for view " + viewId);
    }

    @Test
    public void eachCardOpensTheSameActivityAsBefore() {
        // The targets of the launch* methods that MainActivity had before the split.
        Map<Integer, Class<? extends Activity>> expected = new LinkedHashMap<>();
        expected.put(R.id.card_live_data, LiveDataActivity.class);
        expected.put(R.id.card_fault_codes, FaultCodesActivity.class);
        expected.put(R.id.card_fuel_economy, FuelEconomyActivity.class);
        expected.put(R.id.card_emissions, EmissionsActivity.class);
        expected.put(R.id.card_vehicle_history, AutoCheckActivity.class);
        expected.put(R.id.card_vehicle_recalls, RecallActivity.class);
        expected.put(R.id.card_ecu_list, EcuListActivity.class);
        expected.put(R.id.card_vehicle_info, VehicleInfoActivity.class);
        expected.put(R.id.card_copilot, CoPilotActivity.class);
        expected.put(R.id.card_full_scan, ScanActivity.class);
        expected.put(R.id.card_track_mode, TrackModeActivity.class);
        for (Map.Entry<Integer, Class<? extends Activity>> e : expected.entrySet()) {
            assertEquals(e.getValue().getSimpleName(), e.getValue(), card(e.getKey()).target);
        }
        assertEquals("eleven activities plus Test Control", expected.size() + 1, DashboardCards.ALL.size());
    }

    @Test
    public void testControlSwitchesServiceInsteadOfOpeningAnActivity() {
        DashboardCards.Card testControl = card(R.id.card_test_control);
        assertNull(testControl.target);
        assertEquals(DashboardCards.TEST_CONTROL_TITLE, testControl.title);
    }

    @Test
    public void viewIdsAndLogNamesAreUnique() {
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (DashboardCards.Card card : DashboardCards.ALL) {
            assertTrue("duplicate view id for " + card.logName, ids.add(card.viewId));
            assertTrue("duplicate log name " + card.logName, names.add(card.logName));
        }
    }

    @Test
    public void everyCardHasATitleAndAnExplanation() {
        for (DashboardCards.Card card : DashboardCards.ALL) {
            assertTrue(card.logName + " needs a title", card.title != null || card.titleRes != 0);
            assertNotNull(card.logName, card.info);
            assertFalse(card.logName, card.info.isEmpty());
        }
    }

    @Test
    public void localisedTitlesComeFromResources() {
        assertEquals(R.string.safety_recalls, card(R.id.card_vehicle_recalls).titleRes);
        assertNull(card(R.id.card_vehicle_recalls).title);
        assertEquals(R.string.ecu_list_title, card(R.id.card_ecu_list).titleRes);
        assertEquals("VIN Decoder", card(R.id.card_vehicle_info).title);
    }

    @Test
    public void testControlNeedsAnAnsweringEcu() {
        for (ElmProt.STAT stat : ElmProt.STAT.values()) {
            boolean expected = stat == ElmProt.STAT.ECU_DETECTED
                    || stat == ElmProt.STAT.CONNECTED
                    || stat == ElmProt.STAT.ECU_SELECTED;
            assertEquals(stat.name(), expected, DashboardCards.testControlAllowed(stat));
        }
        assertFalse(DashboardCards.testControlAllowed(null));
    }
}
