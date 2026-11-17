package com.obddroid.ui.activities;

import android.app.Activity;

import com.obddroid.R;
import com.obddroid.features.copilot.ui.CoPilotActivity;
import com.obddroid.features.emissions.ui.EmissionsActivity;
import com.obddroid.features.fueleconomy.ui.FuelEconomyActivity;
import com.obddroid.features.recalls.ui.RecallActivity;
import com.obddroid.features.trackmode.TrackModeActivity;
import com.obddroid.features.vehiclehistory.ui.AutoCheckActivity;
import com.obddroid.obd.ElmProt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The cards on the main screen's dashboard: which view, what a long press explains, and where a tap goes.
 * <p>
 * {@link DashboardUiHelper} wires the views from this table. Every card but Test Control opens an
 * activity; Test Control switches the main screen to OBD service 08 once the ECU answered.
 */
final class DashboardCards {

    /** One dashboard card. */
    static final class Card {
        /** View id in startup_layout. */
        final int viewId;
        /** Short name for the log ("Live Data card clicked!"). */
        final String logName;
        /** Long-press dialog title, or null when {@link #titleRes} is used. */
        final String title;
        /** Long-press dialog title as a string resource, or 0. */
        final int titleRes;
        /** Long-press dialog text. */
        final String info;
        /** Activity a tap opens, or null for Test Control. */
        final Class<? extends Activity> target;

        private Card(int viewId, String logName, String title, int titleRes, String info,
                     Class<? extends Activity> target) {
            this.viewId = viewId;
            this.logName = logName;
            this.title = title;
            this.titleRes = titleRes;
            this.info = info;
            this.target = target;
        }
    }

    /** Toolbar title of the Test Control service view. */
    static final String TEST_CONTROL_TITLE = "Test Control";

    /** All cards, in layout order. */
    static final List<Card> ALL = Collections.unmodifiableList(Arrays.asList(
            new Card(R.id.card_live_data, "Live Data", "Live Data", 0,
                    "Monitor real-time sensor values from the vehicle, including GPS and motion telemetry.",
                    LiveDataActivity.class),
            new Card(R.id.card_test_control, "Test Control", TEST_CONTROL_TITLE, 0,
                    "Run diagnostic tests and actuator controls supported by the vehicle.",
                    null),
            new Card(R.id.card_fault_codes, "Fault Codes", "Fault Codes", 0,
                    "Read, decode, and clear diagnostic trouble codes stored by the vehicle.",
                    FaultCodesActivity.class),
            new Card(R.id.card_fuel_economy, "Fuel Economy", "Fuel Economy", 0,
                    "Track MPG, fuel level, and driving range statistics.",
                    FuelEconomyActivity.class),
            new Card(R.id.card_emissions, "Emissions", "Emissions Diagnostics", 0,
                    "Check monitor readiness status and IUMPR performance data. Verify if your vehicle is ready for emissions testing.",
                    EmissionsActivity.class),
            new Card(R.id.card_vehicle_history, "Vehicle History", "Vehicle History", 0,
                    "Get comprehensive vehicle history reports powered by AutoCheck. Check for accidents, ownership history, title status, recalls, and more.",
                    AutoCheckActivity.class),
            new Card(R.id.card_vehicle_recalls, "Safety Recalls", null, R.string.safety_recalls,
                    "Look up open safety recalls using the official NHTSA database.",
                    RecallActivity.class),
            new Card(R.id.card_ecu_list, "ECU List", null, R.string.ecu_list_title,
                    "Discover and view all Electronic Control Units (ECUs) in your vehicle. Shows ECU names, addresses, calibration IDs, and supported features.",
                    EcuListActivity.class),
            new Card(R.id.card_vehicle_info, "Vehicle Info", "VIN Decoder", 0,
                    "View comprehensive decoded VIN information from the NHTSA vPIC database. See all available details about your vehicle including specifications, safety features, and manufacturing data.",
                    VehicleInfoActivity.class),
            new Card(R.id.card_copilot, "CoPilot", "CoPilot AI Assistant", 0,
                    "Get intelligent assistance with vehicle diagnostics. Ask questions about scan results, fault codes, repair recommendations, and get guided troubleshooting help.",
                    CoPilotActivity.class),
            new Card(R.id.card_full_scan, "Full Scan", "Full Vehicle Scan", 0,
                    "Perform a comprehensive scan of all vehicle systems. Reads live data, fault codes, test results, and vehicle information in one operation.",
                    ScanActivity.class),
            new Card(R.id.card_track_mode, "Track Mode", "Track Mode", 0,
                    "Racing lap timer with telemetry recording. Track your performance on race circuits with automatic lap timing, sector splits, G-force monitoring, and detailed telemetry analysis.",
                    TrackModeActivity.class)
    ));

    private DashboardCards() {
        // table only
    }

    /**
     * Test Control may start once the ECU answered or was selected; the menu's service items do not
     * accept ECU_SELECTED, the card does.
     */
    static boolean testControlAllowed(ElmProt.STAT ecuState) {
        return ecuState == ElmProt.STAT.ECU_DETECTED
                || ecuState == ElmProt.STAT.CONNECTED
                || ecuState == ElmProt.STAT.ECU_SELECTED;
    }
}
