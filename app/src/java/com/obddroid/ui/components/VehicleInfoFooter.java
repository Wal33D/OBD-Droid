package com.obddroid.ui.components;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import com.obddroid.utils.VehicleData;
import com.obddroid.obd.ElmProt;
import com.obddroid.services.VehicleManager;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.common.ProcessVariables.PvChangeListener;
import com.obddroid.common.ProcessVariables.PvChangeEvent;
import com.obddroid.common.ProcessVariables.ProcessVar;
import com.obddroid.common.ProcessVariables.TypedPvList;
import com.obddroid.utils.DebugLog;

/**
 * Footer bar that displays decoded vehicle information.
 * <p>
 * Collapsed it is one row ({@link VehicleInfoHeader}): manufacturer logo or letter, vehicle name and a
 * status line. Tapping it slides open {@link VehicleDetailsPanel} with the decoded vehicle and the Mode 9
 * counters ({@link FooterExpandAnimator}). This class keeps the connection state, follows
 * {@link VehicleManager} and the Mode 9 store, and decides what the header shows.
 */
public class VehicleInfoFooter extends LinearLayout implements PvChangeListener
{
    private static final String TAG = "VehicleInfoFooter";

    private VehicleInfoHeader header;
    private VehicleDetailsPanel detailsPanel;
    private LinearLayout expandedContainer;
    private VehicleManager.VehicleChangeListener vehicleListener;
    private boolean isConnected = false;
    private boolean isEcuConnected = false;
    private boolean isExpanded = false;
    private VehicleData currentVehicleData;
    private View overlayView;
    private TypedPvList<Object, ProcessVar> vehicleInfoStore;
    private VehicleInfoReadyListener vehicleInfoReadyListener;

    /**
     * Collapses the footer on system back. Enabled only while the footer is expanded, so a back gesture
     * with the footer closed goes straight to the system (predictive back animation, targetSdk 36).
     */
    private final OnBackPressedCallback collapseOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            collapse();
        }
    };

    /**
     * Listener for notifying when the footer has finished displaying decoded vehicle data.
     */
    public interface VehicleInfoReadyListener {
        void onVehicleInfoReady();
    }

    public VehicleInfoFooter(Context context)
    {
        super(context);
        init();
    }

    public VehicleInfoFooter(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    public VehicleInfoFooter(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init()
    {
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundColor(Color.parseColor("#212121")); // Match navbar dark grey

        // Set elevation higher than snackbars so they slide from behind
        setElevation(8f);

        addLine("#333333"); // top border

        header = new VehicleInfoHeader(getContext());
        addView(header.view(), new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        addLine("#1A1A1A"); // separates the header from the expanded panel

        // Expanded panel: a fixed-height scroll view, hidden until the header is tapped
        expandedContainer = new LinearLayout(getContext());
        expandedContainer.setOrientation(LinearLayout.VERTICAL);
        expandedContainer.setVisibility(View.GONE);
        expandedContainer.setBackgroundColor(Color.parseColor("#1A1A1A"));

        ScrollView expandedScrollView = new ScrollView(getContext());
        expandedScrollView.setFillViewport(false);
        expandedScrollView.setBackgroundColor(Color.parseColor("#1A1A1A"));

        LinearLayout expandedContentLayout = new LinearLayout(getContext());
        expandedContentLayout.setOrientation(LinearLayout.VERTICAL);
        expandedContentLayout.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(16));
        expandedScrollView.addView(expandedContentLayout, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        detailsPanel = new VehicleDetailsPanel(expandedContentLayout);

        expandedContainer.addView(expandedScrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(500)
        ));
        addView(expandedContainer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        addLine("#333333"); // bottom border

        // Tapping the header expands or collapses, connected or not
        View headerRow = header.view();
        headerRow.setClickable(true);
        headerRow.setFocusable(true);
        headerRow.setOnClickListener(v -> {
            DebugLog.d(TAG, "Footer clicked!");
            v.setAlpha(0.8f);
            v.animate().alpha(1f).setDuration(200).start();
            toggleExpanded();
        });
        DebugLog.d(TAG, "VehicleInfoFooter initialized - click listener set");

        setVisibility(View.VISIBLE);

        vehicleListener = new VehicleManager.SimpleVehicleChangeListener() {
            @Override
            public void onVINChanged(String vin) {
                DebugLog.d(TAG, "VIN changed: " + vin);
                post(() -> {
                    isConnected = true;
                    header.showVin(vin);
                });
            }

            @Override
            public void onVehicleDecoded(VehicleData vehicleData) {
                DebugLog.d(TAG, "Vehicle decoded: " + vehicleData.getDisplayName());
                post(() -> updateVehicleDisplay(VehicleManager.getInstance().getCurrentVIN(), vehicleData));
            }

            @Override
            public void onVehicleDisconnected() {
                DebugLog.d(TAG, "Vehicle disconnected");
                post(() -> {
                    isConnected = false;
                    currentVehicleData = null;

                    // Collapse at once if expanded
                    if (isExpanded) {
                        setExpandedState(false);
                        header.resetArrow();
                        expandedContainer.setVisibility(View.GONE);
                    }

                    updateConnectionDisplay();
                });
            }

            @Override
            public void onECUConnectionChanged(ElmProt.STAT state) {
                DebugLog.d(TAG, "ECU connection state changed: " + state);
                post(() -> onEcuStateChanged(state));
            }

            @Override
            public void onVINRetrievalFailed() {
                DebugLog.d(TAG, "VIN retrieval failed - Mode 9 not supported");
                post(() -> {
                    isConnected = false;
                    currentVehicleData = null;
                    updateConnectionDisplay();
                });
            }

            @Override
            public void onDecodingStarted() {
                DebugLog.d(TAG, "VIN decoding started");
                post(() -> header.showDecoding());
            }

            @Override
            public void onDecodingError(String error) {
                Log.e(TAG, "VIN decoding error: " + error);
                post(() -> {
                    isConnected = false;
                    currentVehicleData = null;
                    // Show the error briefly, then the connection state
                    header.showDecodingError(error);
                    postDelayed(() -> updateConnectionDisplay(), 3000);
                });
            }
        };

        VehicleManager.getInstance().addListener(vehicleListener);

        // Mode 9 data: a backup path for the VIN, and the counters in the expanded panel
        vehicleInfoStore = ObdProt.getDataService().getTypedStoreForService(ObdProt.OBD_SVC_VEH_INFO);
        if (vehicleInfoStore != null) {
            vehicleInfoStore.addPvChangeListener(this);
        }

        // Start disconnected; updated when the ECU connects
        isEcuConnected = false;
    }

    private void addLine(String color) {
        View line = new View(getContext());
        line.setBackgroundColor(Color.parseColor(color));
        addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)));
    }

    /**
     * Follows the ECU link. NODATA is transient: with a cached VIN and vehicle the footer stays as it is
     * instead of flickering grey. On (re)connection with cached data the vehicle is shown again.
     */
    private void onEcuStateChanged(ElmProt.STAT state) {
        VehicleManager vm = VehicleManager.getInstance();
        if (state == ElmProt.STAT.NODATA) {
            if (vm.getCurrentVIN() != null && vm.getCurrentVehicleData() != null) {
                DebugLog.d(TAG, "NODATA received but keeping ECU connected (cached data present)");
                isEcuConnected = true;
                return;
            }
            isEcuConnected = false;
        } else {
            isEcuConnected = (state == ElmProt.STAT.ECU_DETECTED ||
                              state == ElmProt.STAT.ECU_SELECTED ||
                              state == ElmProt.STAT.CONNECTED);
        }

        if (isEcuConnected) {
            String cachedVin = vm.getCurrentVIN();
            VehicleData cachedData = vm.getCurrentVehicleData();
            if (cachedVin != null && cachedData != null) {
                DebugLog.d(TAG, "ECU reconnected with cached vehicle data - restoring display");
                updateVehicleDisplay(cachedVin, cachedData);
                return;
            }
        }

        updateConnectionDisplay();
    }

    /**
     * Register a listener to be notified when decoded vehicle data has been rendered in the footer.
     * @param listener callback invoked on the UI thread when vehicle info is ready
     */
    public void setVehicleInfoReadyListener(VehicleInfoReadyListener listener) {
        this.vehicleInfoReadyListener = listener;

        if (listener != null && currentVehicleData != null) {
            // Ensure UI has already been updated before invoking the callback
            post(listener::onVehicleInfoReady);
        }
    }

    /**
     * @return true if decoded vehicle data is currently displayed in the footer.
     */
    public boolean hasVehicleData() {
        return currentVehicleData != null && isConnected;
    }

    private void notifyVehicleInfoReady() {
        if (vehicleInfoReadyListener != null) {
            vehicleInfoReadyListener.onVehicleInfoReady();
        }
    }

    /**
     * Mode 9 update. Backup VIN detection: a 17-character VIN here is passed to {@link VehicleManager}
     * in case MainActivity's listener missed it. Mode 9 data is static, so the panel is not redrawn.
     */
    @Override
    public void pvChanged(PvChangeEvent event) {
        DebugLog.d(TAG, "Mode 9 data update received: " + event.getKey());

        try {
            Object eventValue = event.getValue();
            if (eventValue instanceof EcuDataPv) {
                EcuDataPv dataPv = (EcuDataPv) eventValue;
                String vin = VehicleInfoFormat.vinFromMode9(
                    String.valueOf(dataPv.get(EcuDataPv.FID_DESCRIPT)), dataPv.get(EcuDataPv.FID_VALUE));
                if (vin != null) {
                    VehicleManager vm = VehicleManager.getInstance();
                    String currentVin = vm.getCurrentVIN();
                    // Only set VIN if not already set (prevents duplicate calls)
                    if (currentVin == null || !currentVin.equals(vin)) {
                        Log.i(TAG, "VIN DETECTED in VehicleInfoFooter (backup path): " + vin);
                        vm.setVIN(vin);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for VIN in pvChanged", e);
        }
    }

    /**
     * Set the overlay view that should be shown/hidden when footer expands/collapses
     * @param overlay The overlay view from the parent layout
     */
    public void setOverlayView(View overlay) {
        this.overlayView = overlay;
        // Tapping the overlay collapses the footer
        if (overlayView != null) {
            overlayView.setOnClickListener(v -> {
                if (isExpanded) {
                    toggleExpanded();
                }
            });
        }
    }

    @Override
    protected void onAttachedToWindow()
    {
        super.onAttachedToWindow();
        // Clear any stale display first to prevent flash
        currentVehicleData = null;
        isConnected = false;

        // Immediately show "No Vehicle Connected" to prevent flash of old data
        updateConnectionDisplay();

        // Re-register when attached
        if (vehicleListener != null) {
            VehicleManager.getInstance().addListener(vehicleListener);
        }
        if (vehicleInfoStore != null) {
            vehicleInfoStore.addPvChangeListener(this);
        }

        // Get current state (will update if actually connected)
        updateVehicleInfo();
    }

    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();
        if (vehicleListener != null) {
            VehicleManager.getInstance().removeListener(vehicleListener);
        }
        if (vehicleInfoStore != null) {
            vehicleInfoStore.removePvChangeListener(this);
        }

        vehicleInfoReadyListener = null;
    }

    private void updateVehicleInfo()
    {
        VehicleManager vm = VehicleManager.getInstance();

        // Not connected: don't show old data
        if (!vm.isECUConnected()) {
            updateConnectionDisplay();
            return;
        }

        String vin = vm.getCurrentVIN();
        VehicleData data = vm.getCurrentVehicleData();

        if (vin != null && data != null)
        {
            updateVehicleDisplay(vin, data);
        } else {
            updateConnectionDisplay();
        }
    }

    /**
     * Make system back collapse this footer while it is expanded. Replaces the activities' old
     * onBackPressed() overrides, which are not called once predictive back is enabled.
     */
    public void attachBackHandler(ComponentActivity activity) {
        activity.getOnBackPressedDispatcher().addCallback(activity, collapseOnBack);
    }

    private void setExpandedState(boolean expanded) {
        isExpanded = expanded;
        collapseOnBack.setEnabled(expanded);
    }

    private void toggleExpanded() {
        setExpandedState(!isExpanded);
        DebugLog.d(TAG, "toggleExpanded: isExpanded = " + isExpanded);

        header.animateArrow(isExpanded);

        if (isExpanded) {
            detailsPanel.render(currentVehicleData, vehicleInfoStore);
            FooterExpandAnimator.expand(expandedContainer, overlayView, getWidth());
        } else {
            FooterExpandAnimator.collapse(expandedContainer, overlayView);
        }
    }

    /**
     * Check if the footer is currently expanded
     * @return true if expanded, false otherwise
     */
    public boolean isExpanded() {
        return isExpanded;
    }

    /**
     * Collapse the footer if it's expanded
     */
    public void collapse() {
        if (isExpanded) {
            toggleExpanded();
        }
    }

    /**
     * Expand the footer if it's collapsed
     */
    public void expand() {
        if (!isExpanded) {
            toggleExpanded();
        }
    }

    private void updateVehicleDisplay(String vin, VehicleData vehicleData)
    {
        if (vehicleData == null)
        {
            header.showUndecodedVin(vin);
            return;
        }

        try {
            isConnected = true;
            currentVehicleData = vehicleData;
            header.showVehicle(vin, vehicleData);

            if (isExpanded) {
                detailsPanel.render(currentVehicleData, vehicleInfoStore);
            }

            notifyVehicleInfoReady();

        } catch (Exception e) {
            Log.e(TAG, "Error updating vehicle display", e);
            header.showVehicleError();
        }
    }

    /** The header for a connection without a decoded vehicle; a decoded vehicle stays as it is. */
    private void updateConnectionDisplay() {
        if (isConnected) {
            return;
        }
        boolean vinFailed = isEcuConnected && VehicleManager.getInstance().hasVINRetrievalFailed();
        header.showConnection(VehicleInfoFormat.connectionLook(isEcuConnected, vinFailed));
    }

    private int dpToPx(int dp)
    {
        return VehicleInfoHeader.dpToPx(getContext(), dp);
    }
}
