package com.obddroid.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.obddroid.R;
import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainMenuControllerTest {

    /** Records what the menu asked for, one line per call. */
    private static final class RecordingHost implements MainMenuController.Host {
        final List<String> calls = new ArrayList<>();

        @Override public void goHome() { calls.add("home"); }
        @Override public void selectAdapter() { calls.add("selectAdapter"); }
        @Override public void confirmDisconnect() { calls.add("confirmDisconnect"); }
        @Override public void openSettings() { calls.add("settings"); }
        @Override public void showHelp() { calls.add("help"); }
        @Override public void startService(int obdService, CharSequence title) {
            calls.add("service " + obdService + " " + title);
        }
        @Override public void warnEcuNotReady() { calls.add("warnEcuNotReady"); }
    }

    private RecordingHost host;
    private MainConnectionState connection;
    private List<String> visibility;
    private MainMenuController menu;

    @Before
    public void setUp() {
        host = new RecordingHost();
        connection = new MainConnectionState();
        visibility = new ArrayList<>();
        menu = new MainMenuController(host, connection,
                (id, visible) -> visibility.add(name(id) + "=" + visible));
    }

    private static String name(int id) {
        if (id == R.id.secure_connect_scan) return "connect";
        if (id == R.id.disconnect) return "disconnect";
        return "id" + id;
    }

    @Test
    public void connectedShowsDisconnectIconOnly() {
        menu.showConnected(true);
        assertEquals(Arrays.asList("connect=false", "disconnect=true"), visibility);
    }

    @Test
    public void disconnectedShowsConnectIconOnly() {
        menu.showConnected(false);
        assertEquals(Arrays.asList("disconnect=false", "connect=true"), visibility);
    }

    @Test
    public void onlineDemoAndFileCountAsConnected() {
        assertTrue(MainMenuController.showsConnected(MainActivity.MODE.ONLINE));
        assertTrue(MainMenuController.showsConnected(MainActivity.MODE.DEMO));
        assertTrue(MainMenuController.showsConnected(MainActivity.MODE.FILE));
        assertFalse(MainMenuController.showsConnected(MainActivity.MODE.OFFLINE));
    }

    @Test
    public void prepareFollowsTheMode() {
        menu.onPrepare();
        connection.setMode(MainActivity.MODE.DEMO);
        menu.onPrepare();
        assertEquals(Arrays.asList("disconnect=false", "connect=true", "connect=false", "disconnect=true"), visibility);
    }

    @Test
    public void toolbarItemsGoToTheHost() {
        assertTrue(menu.onItemSelected(android.R.id.home, "x"));
        assertTrue(menu.onItemSelected(R.id.service_home, "x"));
        assertTrue(menu.onItemSelected(R.id.secure_connect_scan, "x"));
        assertTrue(menu.onItemSelected(R.id.disconnect, "x"));
        assertTrue(menu.onItemSelected(R.id.settings, "x"));
        assertTrue(menu.onItemSelected(R.id.action_info, "x"));
        assertTrue(menu.onItemSelected(R.id.service_none, "None"));
        assertEquals(Arrays.asList("home", "home", "selectAdapter", "confirmDisconnect", "settings", "help",
                "service " + ObdProt.OBD_SVC_NONE + " None"), host.calls);
    }

    @Test
    public void serviceItemsWaitForTheEcu() {
        connection.setEcuConnectionState(ElmProt.STAT.CONNECTING);
        assertTrue(menu.onItemSelected(R.id.service_data, "Data"));
        assertTrue(menu.onItemSelected(R.id.service_testcontrol, "Tests"));
        assertTrue(menu.onItemSelected(R.id.service_codes, "Codes"));
        assertEquals(Arrays.asList("warnEcuNotReady", "warnEcuNotReady", "warnEcuNotReady"), host.calls);
    }

    @Test
    public void serviceItemsStartOnceTheEcuAnswered() {
        connection.setEcuConnectionState(ElmProt.STAT.ECU_DETECTED);
        menu.onItemSelected(R.id.service_data, "Data");
        connection.setEcuConnectionState(ElmProt.STAT.CONNECTED);
        menu.onItemSelected(R.id.service_testcontrol, "Tests");
        menu.onItemSelected(R.id.service_codes, "Codes");
        assertEquals(Arrays.asList(
                "service " + ObdProt.OBD_SVC_DATA + " Data",
                "service " + ObdProt.OBD_SVC_CTRL_MODE + " Tests",
                "service " + ObdProt.OBD_SVC_READ_CODES + " Codes"), host.calls);
    }

    @Test
    public void unknownItemIsLeftToTheActivity() {
        assertFalse(menu.onItemSelected(R.id.chart_selected, "Chart"));
        assertTrue(host.calls.isEmpty());
    }

    @Test
    public void selectionActionsMapToViewModes() {
        assertEquals(MainActivity.DATA_VIEW_MODE.CHART, MainMenuController.viewModeForAction(R.id.chart_selected));
        assertEquals(MainActivity.DATA_VIEW_MODE.HEADUP, MainMenuController.viewModeForAction(R.id.hud_selected));
        assertEquals(MainActivity.DATA_VIEW_MODE.DASHBOARD, MainMenuController.viewModeForAction(R.id.dashboard_selected));
        assertEquals(MainActivity.DATA_VIEW_MODE.FILTERED, MainMenuController.viewModeForAction(R.id.filter_selected));
        assertNull(MainMenuController.viewModeForAction(R.id.settings));
    }

    @Test
    public void withoutAMenuVisibilityChangesAreIgnored() {
        // The production constructor writes to the attached Menu; before onCreateOptionsMenu there is none.
        MainMenuController real = new MainMenuController(host, connection);
        real.showConnected(true);
        real.onPrepare();
        assertTrue(host.calls.isEmpty());
    }
}
