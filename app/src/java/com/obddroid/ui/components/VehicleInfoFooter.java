package com.obddroid.ui.components;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import com.obddroid.utils.VehicleData;
import com.obddroid.obd.ElmProt;
import com.automotivelogolibrary.AutomotiveLogoLibraryAndroid;
import com.obddroid.services.VehicleManager;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.obd.Messages;
import com.obddroid.common.ProcessVariables.PvChangeListener;
import com.obddroid.common.ProcessVariables.PvChangeEvent;
import com.obddroid.common.ProcessVariables.ProcessVar;
import com.obddroid.common.ProcessVariables.TypedPvList;
import com.obddroid.utils.DebugLog;
import java.beans.PropertyChangeEvent;
import java.util.Locale;
import java.util.Map;

/**
 * Footer bar that displays decoded vehicle information
 * Shows manufacturer icon/letter and vehicle name when VIN is decoded
 */
public class VehicleInfoFooter extends LinearLayout implements PvChangeListener
{
    private static final String TAG = "VehicleInfoFooter";

    private TextView manufacturerIcon;
    private ImageView manufacturerLogo;
    private LinearLayout iconContainer;
    private TextView vehicleInfo;
    private TextView connectionStatus;
    private View divider;
    private LinearLayout contentLayout;
    private LinearLayout expandedContainer;
    private ScrollView expandedScrollView;
    private LinearLayout expandedContentLayout;
    private VehicleManager.VehicleChangeListener vehicleListener;
    private boolean isConnected = false;
    private boolean isEcuConnected = false;
    private boolean isExpanded = false;
    private VehicleData currentVehicleData;
    private View expandIndicator;
    private View statusDot;
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
        // Set up the layout
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundColor(Color.parseColor("#212121")); // Match navbar dark grey

        // Set elevation higher than snackbars so they slide from behind
        setElevation(8f);

        // Add top border line
        divider = new View(getContext());
        divider.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        );
        addView(divider, dividerParams);

        // Create content layout
        contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);
        contentLayout.setGravity(Gravity.CENTER_VERTICAL);
        contentLayout.setPadding(dpToPx(16), dpToPx(2), dpToPx(16), dpToPx(2));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        addView(contentLayout, contentParams);

        // Create manufacturer icon container
        iconContainer = new LinearLayout(getContext());
        iconContainer.setGravity(Gravity.CENTER);
        // No background - transparent for PNG logos
        iconContainer.setBackground(null);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(
            dpToPx(60),
            dpToPx(60)
        );
        iconContainerParams.rightMargin = dpToPx(12);

        // Create manufacturer logo ImageView
        manufacturerLogo = new ImageView(getContext());
        manufacturerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        manufacturerLogo.setPadding(0, 0, 0, 0);
        manufacturerLogo.setBackground(null);
        manufacturerLogo.setVisibility(View.GONE);

        // Create manufacturer icon text (fallback when no logo)
        manufacturerIcon = new TextView(getContext());
        manufacturerIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        manufacturerIcon.setTextColor(Color.parseColor("#00ACC1")); // Cyan accent
        manufacturerIcon.setTypeface(Typeface.DEFAULT_BOLD);
        manufacturerIcon.setGravity(Gravity.CENTER);
        manufacturerIcon.setText("?");

        // Add both to container (we'll show one at a time)
        iconContainer.addView(manufacturerLogo, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));
        iconContainer.addView(manufacturerIcon, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));

        // Create info container
        LinearLayout infoContainer = new LinearLayout(getContext());
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );

        // Create vehicle info text
        vehicleInfo = new TextView(getContext());
        vehicleInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        vehicleInfo.setTextColor(Color.parseColor("#FFFFFF"));
        vehicleInfo.setTypeface(Typeface.DEFAULT_BOLD);
        vehicleInfo.setText("No Vehicle Connected");
        vehicleInfo.setSingleLine(true);
        vehicleInfo.setEllipsize(android.text.TextUtils.TruncateAt.END);

        // Create connection status text
        connectionStatus = new TextView(getContext());
        connectionStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        connectionStatus.setTextColor(Color.parseColor("#888888"));
        connectionStatus.setText("Waiting for OBD connection...");
        connectionStatus.setSingleLine(true);

        // Add to info container
        infoContainer.addView(vehicleInfo);
        infoContainer.addView(connectionStatus);

        // Add all to content layout
        contentLayout.addView(iconContainer, iconContainerParams);
        contentLayout.addView(infoContainer, infoParams);

        // Create expand indicator (arrow)
        expandIndicator = new TextView(getContext());
        ((TextView)expandIndicator).setText("▼");
        ((TextView)expandIndicator).setTextColor(Color.parseColor("#888888"));
        ((TextView)expandIndicator).setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        expandParams.leftMargin = dpToPx(8);
        expandParams.gravity = Gravity.CENTER_VERTICAL;
        contentLayout.addView(expandIndicator, expandParams);

        // Create status indicator dot (green circle for connectivity)
        statusDot = new View(getContext());
        statusDot.setBackgroundResource(android.R.drawable.presence_offline);
        statusDot.getBackground().setTint(Color.parseColor("#888888")); // Gray when disconnected
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
            dpToPx(8),
            dpToPx(8)
        );
        dotParams.leftMargin = dpToPx(8);
        dotParams.gravity = Gravity.CENTER_VERTICAL;
        contentLayout.addView(statusDot, dotParams);

        // Add bottom divider for header separation
        View headerDivider = new View(getContext());
        headerDivider.setBackgroundColor(Color.parseColor("#1A1A1A")); // Subtle dark line
        LinearLayout.LayoutParams headerDividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        );
        addView(headerDivider, headerDividerParams);

        // Create expanded container
        expandedContainer = new LinearLayout(getContext());
        expandedContainer.setOrientation(LinearLayout.VERTICAL);
        expandedContainer.setVisibility(View.GONE);
        expandedContainer.setBackgroundColor(Color.parseColor("#1A1A1A")); // Slightly lighter than footer

        // Create ScrollView for expanded content
        expandedScrollView = new ScrollView(getContext());
        expandedScrollView.setFillViewport(false);
        expandedScrollView.setBackgroundColor(Color.parseColor("#1A1A1A"));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(500) // Optimal height for viewing
        );

        // Create expanded content layout inside ScrollView
        expandedContentLayout = new LinearLayout(getContext());
        expandedContentLayout.setOrientation(LinearLayout.VERTICAL);
        expandedContentLayout.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(16));

        // Add content layout to ScrollView
        expandedScrollView.addView(expandedContentLayout, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Add ScrollView to expanded container
        expandedContainer.addView(expandedScrollView, scrollParams);

        // Add expanded container to main layout
        LinearLayout.LayoutParams expandedParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        addView(expandedContainer, expandedParams);

        // Add bottom border line
        View bottomDivider = new View(getContext());
        bottomDivider.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams bottomDividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        );
        addView(bottomDivider, bottomDividerParams);

        // Make content layout clickable to expand/collapse
        contentLayout.setClickable(true);
        contentLayout.setFocusable(true);
        contentLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DebugLog.d(TAG, "Footer clicked!");
                // Allow expanding even without connection to show demo
                // Add subtle press feedback
                v.setAlpha(0.8f);
                v.animate().alpha(1f).setDuration(200).start();
                toggleExpanded();
            }
        });
        DebugLog.d(TAG, "VehicleInfoFooter initialized - click listener set");

        // Always visible
        setVisibility(View.VISIBLE);

        // Create vehicle change listener
        vehicleListener = new VehicleManager.SimpleVehicleChangeListener() {
            @Override
            public void onVINChanged(String vin) {
                DebugLog.d(TAG, "VIN changed: " + vin);
                post(() -> {
                    isConnected = true;
                    vehicleInfo.setText("VIN: " + vin);
                    connectionStatus.setText("Decoding vehicle information...");
                    connectionStatus.setTextColor(Color.parseColor("#FFA726")); // Orange for loading
                    if (statusDot != null) {
                        statusDot.getBackground().setTint(Color.parseColor("#FFA726")); // Orange while loading
                    }
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

                    // Collapse if expanded
                    if (isExpanded) {
                        setExpandedState(false);
                        ((TextView)expandIndicator).setRotation(0f);
                        expandedContainer.setVisibility(View.GONE);
                    }

                    // Update display based on ECU connection
                    updateConnectionDisplay();
                });
            }

            @Override
            public void onECUConnectionChanged(ElmProt.STAT state) {
                DebugLog.d(TAG, "ECU connection state changed: " + state);
                post(() -> {
                    // NODATA is a transient state - keep ECU connected if we have cached data
                    // This prevents the footer from showing as disconnected during temporary NODATA
                    boolean wasConnected = isEcuConnected;

                    if (state == ElmProt.STAT.NODATA) {
                        // Check if we have cached vehicle data
                        VehicleManager vm = VehicleManager.getInstance();
                        String cachedVin = vm.getCurrentVIN();
                        VehicleData cachedData = vm.getCurrentVehicleData();

                        if (cachedVin != null && cachedData != null) {
                            // Keep ECU connected state during NODATA if we have cached data
                            // This prevents the footer from flickering grey during transient NODATA states
                            DebugLog.d(TAG, "NODATA received but keeping ECU connected (cached data present)");
                            isEcuConnected = true;
                            // Don't update display - keep existing state
                            return;
                        } else {
                            // No cached data - treat NODATA as disconnected
                            isEcuConnected = false;
                        }
                    } else {
                        isEcuConnected = (state == ElmProt.STAT.ECU_DETECTED ||
                                          state == ElmProt.STAT.ECU_SELECTED ||
                                          state == ElmProt.STAT.CONNECTED);
                    }

                    // If ECU connected and we have cached vehicle data, restore full display
                    // This handles reconnection scenarios where VIN/data is cached
                    if (isEcuConnected) {
                        VehicleManager vm = VehicleManager.getInstance();
                        String cachedVin = vm.getCurrentVIN();
                        VehicleData cachedData = vm.getCurrentVehicleData();

                        if (cachedVin != null && cachedData != null) {
                            // We have cached data - restore full vehicle display with green indicator
                            DebugLog.d(TAG, "ECU reconnected with cached vehicle data - restoring display");
                            updateVehicleDisplay(cachedVin, cachedData);
                            return; // Don't call updateConnectionDisplay
                        }
                    }

                    // Otherwise, update connection status normally
                    updateConnectionDisplay();
                });
            }

            @Override
            public void onVINRetrievalFailed() {
                DebugLog.d(TAG, "VIN retrieval failed - Mode 9 not supported");
                post(() -> {
                    // Clear connected flag since we don't have VIN
                    isConnected = false;
                    currentVehicleData = null;
                    // Update display without showing repeated notifications
                    updateConnectionDisplay();
                });
            }

            @Override
            public void onDecodingStarted() {
                DebugLog.d(TAG, "VIN decoding started");
                post(() -> {
                    connectionStatus.setText("Decoding vehicle information...");
                    connectionStatus.setTextColor(Color.parseColor("#FFA726")); // Orange for loading
                    if (statusDot != null) {
                        statusDot.getBackground().setTint(Color.parseColor("#FFA726")); // Orange while loading
                    }
                });
            }

            @Override
            public void onDecodingError(String error) {
                Log.e(TAG, "VIN decoding error: " + error);
                post(() -> {
                    isConnected = false;
                    currentVehicleData = null;
                    // Show error message briefly, then revert to connection display
                    connectionStatus.setText(error);
                    connectionStatus.setTextColor(Color.parseColor("#FF5722")); // Red for error
                    if (statusDot != null) {
                        statusDot.getBackground().setTint(Color.parseColor("#FF5722")); // Red for error
                    }
                    // After 3 seconds, update to normal connection display
                    postDelayed(() -> updateConnectionDisplay(), 3000);
                });
            }
        };

        // Register as listener with VehicleManager
        VehicleManager.getInstance().addListener(vehicleListener);

        vehicleInfoStore = ObdProt.getDataService().getTypedStoreForService(ObdProt.OBD_SVC_VEH_INFO);
        // Register for Mode 9 data updates
        if (vehicleInfoStore != null) {
            vehicleInfoStore.addPvChangeListener(this);
        }

        // Start with disconnected state - will be updated when ECU connects
        isEcuConnected = false;
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

    @Override
    public void pvChanged(PvChangeEvent event) {
        // Mode 9 data updated - refresh expanded content if visible
        DebugLog.d(TAG, "Mode 9 data update received: " + event.getKey());

        // BACKUP VIN DETECTION: Check if this event contains a VIN
        // This ensures VIN is detected even if MainActivity's listener misses the event
        try {
            Object eventValue = event.getValue();
            if (eventValue instanceof EcuDataPv) {
                EcuDataPv dataPv = (EcuDataPv) eventValue;
                String description = String.valueOf(dataPv.get(EcuDataPv.FID_DESCRIPT));
                Object vinValue = dataPv.get(EcuDataPv.FID_VALUE);

                // Check if this is a VIN field with a valid 17-character VIN
                if (description != null && description.toLowerCase().contains("vehicle identification") &&
                    vinValue != null && vinValue.toString().trim().length() == 17) {
                    String vin = vinValue.toString().trim();
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

        // Mode 9 data is static - no need to refresh OBD DATA tab on every update
        // Only VEHICLE INFO tab needs live updates (handled by setVehicleData)
    }

    public void propertyChange(PropertyChangeEvent evt) {
        // Not used for this implementation
    }

    /**
     * Set the overlay view that should be shown/hidden when footer expands/collapses
     * @param overlay The overlay view from the parent layout
     */
    public void setOverlayView(View overlay) {
        this.overlayView = overlay;
        // Set click listener to collapse footer when overlay is clicked
        if (overlayView != null) {
            overlayView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isExpanded) {
                        toggleExpanded();
                    }
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
        // Re-register for Mode 9 updates
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
        // Unregister when detached
        if (vehicleListener != null) {
            VehicleManager.getInstance().removeListener(vehicleListener);
        }
        // Unregister from Mode 9 updates
        if (vehicleInfoStore != null) {
            vehicleInfoStore.removePvChangeListener(this);
        }

        vehicleInfoReadyListener = null;
    }


    private void updateVehicleInfo()
    {
        VehicleManager vm = VehicleManager.getInstance();

        // Check if ECU is connected first
        if (!vm.isECUConnected()) {
            // Not connected, don't show old data
            updateConnectionDisplay();
            return;
        }

        String vin = vm.getCurrentVIN();
        VehicleData data = vm.getCurrentVehicleData();

        if (vin != null && data != null)
        {
            updateVehicleDisplay(vin, data);
        } else {
            // ECU connected but no vehicle data - update display accordingly
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

        // Animate arrow rotation with smooth easing
        if (expandIndicator != null) {
            ((TextView)expandIndicator).animate()
                .rotation(isExpanded ? 180f : 0f)
                .setDuration(400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        }

        if (isExpanded) {
            DebugLog.d(TAG, "Expanding footer - updating content");
            updateExpandedContent();

            // Show overlay
            if (overlayView != null) {
                overlayView.setVisibility(View.VISIBLE);
                overlayView.setAlpha(0f);
                overlayView.animate().alpha(1f).setDuration(300).start();
            }

            if (expandedContainer != null) {
                // Measure the target height
                expandedContainer.measure(
                    View.MeasureSpec.makeMeasureSpec(getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                final int targetHeight = expandedContainer.getMeasuredHeight();

                // Start with height 0
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) expandedContainer.getLayoutParams();
                params.height = 0;
                expandedContainer.setLayoutParams(params);
                expandedContainer.setVisibility(View.VISIBLE);
                expandedContainer.setAlpha(1f);

                // Animate height with bounce
                ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
                animator.setDuration(500);
                animator.setInterpolator(new OvershootInterpolator(1.2f)); // Bounce factor
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) expandedContainer.getLayoutParams();
                        params.height = (int) animation.getAnimatedValue();
                        expandedContainer.setLayoutParams(params);
                    }
                });
                animator.start();
                DebugLog.d(TAG, "Expanded container made visible with slide+bounce");
            } else {
                Log.e(TAG, "expandedContainer is null!");
            }
        } else {
            DebugLog.d(TAG, "Collapsing footer");

            // Hide overlay
            if (overlayView != null) {
                overlayView.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        overlayView.setVisibility(View.GONE);
                    }
                }).start();
            }

            if (expandedContainer != null) {
                final int startHeight = expandedContainer.getHeight();

                // Animate height collapsing with smooth deceleration
                ValueAnimator animator = ValueAnimator.ofInt(startHeight, 0);
                animator.setDuration(300);
                animator.setInterpolator(new DecelerateInterpolator(1.5f));
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) expandedContainer.getLayoutParams();
                        params.height = (int) animation.getAnimatedValue();
                        expandedContainer.setLayoutParams(params);
                    }
                });
                animator.addListener(new android.animation.Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(android.animation.Animator animation) {}

                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        expandedContainer.setVisibility(View.GONE);
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) expandedContainer.getLayoutParams();
                        params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                        expandedContainer.setLayoutParams(params);
                        DebugLog.d(TAG, "Expanded container hidden");
                    }

                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {}

                    @Override
                    public void onAnimationRepeat(android.animation.Animator animation) {}
                });
                animator.start();
            }
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

    private void updateExpandedContent() {
        expandedContentLayout.removeAllViews();

        // Show Vehicle Info section
        if (currentVehicleData != null) {
            displayVehicleInfo();
            // Show OBD Data section below Vehicle Info
            displayObdData();
        } else {
            // Show single combined empty state when no data is available
            addEmptyStateMessage("Vehicle Information",
                "Vehicle information will appear here once connected and VIN is decoded");
        }
    }

    private void displayVehicleInfo() {
        if (currentVehicleData == null) return;

        // Add VIN section with styled headers and rows
        addStyledSectionHeader("Vehicle Identification");

        // Use NHTSA VIN if available, otherwise fallback to Mode 9 VIN
        String vinToDisplay = currentVehicleData.vin;
        if ((vinToDisplay == null || vinToDisplay.isEmpty()) && vehicleInfoStore != null) {
            // Try to get VIN from Mode 9 data as fallback
            for (Object key : vehicleInfoStore.keySet()) {
                Object pvObj = vehicleInfoStore.get(key);
                if (pvObj instanceof EcuDataPv) {
                    EcuDataPv pv = (EcuDataPv) pvObj;
                    String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                    Object dataValue = pv.get(EcuDataPv.FID_VALUE);

                    if (description != null && dataValue != null &&
                        description.toLowerCase().equals("vehicle identification number")) {
                        vinToDisplay = String.valueOf(dataValue);
                        break;
                    }
                }
            }
        }
        addStyledDetailRow("VIN", vinToDisplay);

        // Add other Mode 9 VIN-related data (like message count) but skip the VIN itself
        if (vehicleInfoStore != null && !vehicleInfoStore.isEmpty()) {
            for (Object key : vehicleInfoStore.keySet()) {
                Object pvObj = vehicleInfoStore.get(key);
                if (pvObj instanceof EcuDataPv) {
                    EcuDataPv pv = (EcuDataPv) pvObj;
                    String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                    Object dataValue = pv.get(EcuDataPv.FID_VALUE);

                    if (description != null && dataValue != null &&
                        (description.toLowerCase().contains("vehicle identification") ||
                         description.toLowerCase().contains("vin")) &&
                        !description.toLowerCase().contains("calibration") &&
                        !description.toLowerCase().equals("vehicle identification number") &&
                        !description.toLowerCase().contains("number of") &&
                        !description.toLowerCase().contains("message count")) {
                        // Add VIN-related Mode 9 data but skip VIN itself and counts
                        addStyledDetailRow(description, String.valueOf(dataValue), null, null);
                    }
                }
            }
        }

        // Format manufacturer name - remove redundant suffixes
        String manufacturerName = currentVehicleData.manufacturer;
        if (manufacturerName != null) {
            // Remove common corporate suffixes for cleaner display
            manufacturerName = manufacturerName
                .replace(" OF NORTH AMERICA", "")
                .replace(", INC.", "")
                .replace(" INC.", "")
                .replace(", LLC", "")
                .replace(" LLC", "")
                .replace(", LTD", "")
                .replace(" LTD", "")
                .replace(" CORPORATION", "")
                .replace(" CORP.", "")
                .trim();
        }
        addStyledDetailRow("Manufacturer", manufacturerName);
        if (currentVehicleData.series != null && !currentVehicleData.series.equals("Not Applicable")) {
            addStyledDetailRow("Series", currentVehicleData.series);
        }
        if (currentVehicleData.trim != null && !currentVehicleData.trim.equals("Not Applicable")) {
            addStyledDetailRow("Trim", currentVehicleData.trim);
        }

        // Add Body/Structure section with cute vehicle icon
        addStyledSectionHeader("Body & Structure");

        // Format body class text for better readability
        String bodyClass = currentVehicleData.bodyClass;
        if (bodyClass != null && bodyClass.contains("/")) {
            // If it has a slash, just use the first part for cleaner display
            bodyClass = bodyClass.split("/")[0].trim();
        }

        // Add cute vehicle icon based on body class
        String vehicleEmoji = getVehicleEmoji(bodyClass);
        if (vehicleEmoji != null) {
            addVehicleTypeHero(vehicleEmoji, bodyClass);
        }

        addStyledDetailRow("Body Class", bodyClass);

        // Format vehicle type similarly
        String vehicleType = currentVehicleData.vehicleType;
        if (vehicleType != null && vehicleType.contains("(")) {
            // Show full vehicle type but formatted better
            vehicleType = vehicleType.replace("(", "\n(");
        }
        addStyledDetailRow("Vehicle Type", vehicleType);
        addStyledDetailRow("Doors", currentVehicleData.doors);
        if (currentVehicleData.wheelBase != null && !currentVehicleData.wheelBase.isEmpty()) {
            addStyledDetailRow("Wheelbase", currentVehicleData.wheelBase + " inches");
        }

        // Add Engine section
        addStyledSectionHeader("Engine & Performance");
        if (currentVehicleData.engineModel != null && !currentVehicleData.engineModel.isEmpty()) {
            addStyledDetailRow("Engine Model", currentVehicleData.engineModel);
        }
        if (currentVehicleData.displacementL != null) {
            String displacement = currentVehicleData.displacementL + "L";
            if (currentVehicleData.displacementCC != null) {
                displacement += " (" + currentVehicleData.displacementCC + "cc)";
            }
            addStyledDetailRow("Displacement", displacement);
        }
        addStyledDetailRow("Cylinders", currentVehicleData.engineCylinders);
        if (currentVehicleData.engineBrakeHp != null && !currentVehicleData.engineBrakeHp.isEmpty()) {
            addStyledDetailRow("Horsepower", currentVehicleData.engineBrakeHp + " hp");
        }
        if (currentVehicleData.topSpeed != null && !currentVehicleData.topSpeed.isEmpty()) {
            addStyledDetailRow("Top Speed", currentVehicleData.topSpeed + " mph");
        }
        addStyledDetailRow("Fuel Type", currentVehicleData.fuelTypePrimary);
        if (currentVehicleData.fuelDeliveryType != null && !currentVehicleData.fuelDeliveryType.isEmpty()) {
            addStyledDetailRow("Fuel Injection", currentVehicleData.fuelDeliveryType);
        }
        if (currentVehicleData.valveTrainDesign != null && !currentVehicleData.valveTrainDesign.isEmpty()) {
            addStyledDetailRow("Valvetrain", currentVehicleData.valveTrainDesign);
        }
        if (currentVehicleData.coolingType != null && !currentVehicleData.coolingType.isEmpty()) {
            addStyledDetailRow("Cooling", currentVehicleData.coolingType);
        }

        // Add Drivetrain section
        addStyledSectionHeader("Drivetrain");
        addStyledDetailRow("Drive Type", currentVehicleData.driveType);
        addStyledDetailRow("Transmission", currentVehicleData.transmissionStyle);
        if (currentVehicleData.transmissionSpeeds != null && !currentVehicleData.transmissionSpeeds.isEmpty()) {
            addStyledDetailRow("Speeds", currentVehicleData.transmissionSpeeds);
        }
        if (currentVehicleData.axles != null && !currentVehicleData.axles.isEmpty()) {
            addStyledDetailRow("Axles", currentVehicleData.axles);
        }
        if (currentVehicleData.steeringLocation != null && !currentVehicleData.steeringLocation.isEmpty()) {
            addStyledDetailRow("Steering", currentVehicleData.steeringLocation);
        }

        // Add Weight section if available
        if (currentVehicleData.gvwr != null || currentVehicleData.curbWeight != null) {
            addStyledSectionHeader("Weight");
            if (currentVehicleData.curbWeight != null && !currentVehicleData.curbWeight.isEmpty()) {
                addStyledDetailRow("Curb Weight", currentVehicleData.curbWeight + " lbs");
            }
            if (currentVehicleData.gvwr != null && !currentVehicleData.gvwr.isEmpty()) {
                addStyledDetailRow("GVWR", currentVehicleData.gvwr);
            }
        }
    }

    private void displayObdData() {
        // Display Mode 9 OBD data with better formatting
        addMode9Section();
    }

    /**
     * Add Mode 9 OBD vehicle information to the expanded view with better formatting
     */
    private void addMode9Section() {
        // Get Mode 9 data from VidPvs
        if (vehicleInfoStore == null || vehicleInfoStore.isEmpty()) {
            // No Mode 9 data available - silently skip (combined with vehicle info empty state)
            return;
        }

        // Categorize Mode 9 data - show EVERYTHING (now storing mnemonic too)
        java.util.Map<String, java.util.List<String[]>> categorizedData = new java.util.LinkedHashMap<>();
        categorizedData.put("System Counters", new java.util.ArrayList<>());

        // Process all Mode 9 items
        java.util.List<Map.Entry<Object, ProcessVar>> entries =
            new java.util.ArrayList<>(vehicleInfoStore.entrySetTyped());
        for (Map.Entry<Object, ProcessVar> entry : entries) {
            ProcessVar storeItem = entry.getValue();
            if (!(storeItem instanceof EcuDataPv)) {
                continue;
            }
            EcuDataPv pv = (EcuDataPv) storeItem;
            if (pv != null) {
                String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                Object dataValue = pv.get(EcuDataPv.FID_VALUE);

                if (description != null && dataValue != null) {
                    // Simple fast processing - NO expensive formatting
                    String label = description; // Use raw description
                    String displayValue = String.valueOf(dataValue); // Simple toString()
                    String mnemonic = null; // Skip mnemonic lookup
                    String pidDisplay = null; // Skip PID formatting

                    // Categorize ALL the data - skip VIN (already shown in Vehicle Info section)
                    // Store as: [label, displayValue, mnemonic]
                    if (description.toLowerCase().contains("vehicle identification") ||
                        description.toLowerCase().contains("vin")) {
                        // Skip VIN - already shown in Vehicle Identification section from NHTSA data
                    } else if (description.contains("Message count") ||
                              description.contains("Number of") ||
                              description.contains("counts_") ||
                              description.contains("numitems") ||
                              description.contains("length")) {
                        // Skip protocol information - not useful for user
                    } else if (description.contains("Monitor") || description.contains("COMP") ||
                              description.contains("Catalyst") || description.contains("O2") ||
                              description.contains("EGR") || description.contains("EVAP") ||
                              description.contains("AIR") || description.contains("Exhaust") ||
                              description.contains("Boost") || description.contains("Fuel") ||
                              description.contains("NMHC") || description.contains("NOx") ||
                              description.contains("PM Filter")) {
                        // Skip emission monitors - don't display them
                    } else if (description.contains("Counter") || description.contains("CNTR") ||
                              description.contains("Ignition") || description.contains("OBD Monitoring Conditions")) {
                        categorizedData.get("System Counters").add(new String[]{label, displayValue, mnemonic, pidDisplay});
                    } else if (description.contains("Calibration") || description.contains("CVN") ||
                              description.contains("CAL-ID") || description.contains("CAL")) {
                        // Skip calibration data - ECU-specific, shown on ECU Modules page instead
                    } else if (description.contains("ECU name") || description.contains("ECU")) {
                        // Skip ECU data - don't display it
                    } else {
                        // Skip other information - not needed
                    }
                }
            }
        }

        // Display categorized data
        boolean hasAnyData = false;
        for (java.util.Map.Entry<String, java.util.List<String[]>> entry : categorizedData.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                hasAnyData = true;
                addStyledSectionHeader(entry.getKey());

                // Sort Vehicle Identification section to put VIN first
                java.util.List<String[]> items = entry.getValue();
                if (entry.getKey().equals("Vehicle Identification")) {
                    items.sort((a, b) -> {
                        // VIN should come first
                        if (a[0].equals("VIN")) return -1;
                        if (b[0].equals("VIN")) return 1;
                        // Otherwise maintain original order
                        return 0;
                    });
                }

                for (String[] item : items) {
                    // item[0]=label, item[1]=value, item[2]=mnemonic
                    addStyledDetailRow(
                        item[0],
                        item[1],
                        item.length > 2 ? item[2] : null,
                        item.length > 3 ? item[3] : null
                    );
                }
            }
        }

        // If no displayable data, silently skip section (combined with vehicle info empty state)
    }

    private String formatLabel(String description) {
        // Clean up and format ALL labels including VIN and message counts
        if (description.toLowerCase().contains("vehicle identification number")) {
            return "VIN";
        } else if (description.contains("Number of VIN items")) {
            return "VIN Item Count";
        } else if (description.contains("Message count VIN")) {
            return "VIN Message Count";
        } else if (description.contains("Message count CAL-ID")) {
            return "CAL-ID Message Count";
        } else if (description.contains("Number of CAL-ID items")) {
            return "CAL-ID Item Count";
        } else if (description.contains("Message count CVN")) {
            return "CVN Message Count";
        } else if (description.contains("Message count IPT")) {
            return "IPT Message Count";
        } else if (description.contains("ECU name length")) {
            return "ECU Name Length";
        } else if (description.contains("ECU name")) {
            return "ECU Name";
        } else if (description.contains("Calibration identifier")) {
            return description.contains("2") ? "Calibration ID 2" : "Calibration ID";
        } else if (description.contains("Calibration verification")) {
            return "CVN (Calibration Verification)";
        } else if (description.contains("OBDCOMP") || description.contains("OBDCOND")) {
            return "OBD Monitor Conditions";
        } else if (description.contains("IGNCNTR")) {
            return "Ignition Cycles";
        } else if (description.contains("CATCOMP")) {
            String bank = extractBank(description);
            return "Catalyst Monitor Completions" + bank;
        } else if (description.contains("CATCOND")) {
            String bank = extractBank(description);
            return "Catalyst Monitor Conditions" + bank;
        } else if (description.contains("O2SCOMP")) {
            String bank = extractBank(description);
            return description.contains("Secondary") ?
                   "Secondary O2 Completions" + bank : "O2 Monitor Completions" + bank;
        } else if (description.contains("O2SCOND")) {
            String bank = extractBank(description);
            return description.contains("Secondary") ?
                   "Secondary O2 Conditions" + bank : "O2 Monitor Conditions" + bank;
        } else if (description.contains("EGRCOMP")) {
            return "EGR Monitor Completions";
        } else if (description.contains("EGRCOND")) {
            return "EGR Monitor Conditions";
        } else if (description.contains("EVAPCOMP")) {
            return "EVAP Monitor Completions";
        } else if (description.contains("EVAPCOND")) {
            return "EVAP Monitor Conditions";
        } else if (description.contains("AIRCOMP")) {
            return "AIR Monitor Completions";
        } else if (description.contains("AIRCOND")) {
            return "AIR Monitor Conditions";
        } else if (description.contains("Completion Counts")) {
            return description.replace("Completion Counts", "Completions");
        } else if (description.contains("Conditions Encountered Counts")) {
            return description.replace("Conditions Encountered Counts", "Conditions");
        }
        return description;
    }

    private String extractBank(String description) {
        if (description.contains("Bank 1")) return " (Bank 1)";
        if (description.contains("Bank 2")) return " (Bank 2)";
        if (description.contains("Bank 3")) return " (Bank 3)";
        if (description.contains("Bank 4")) return " (Bank 4)";
        return "";
    }

    /**
     * Extract mnemonic key from OBD description for looking up in messages.csv
     * Maps human-readable descriptions to their mnemonic keys
     */
    /**
     * Format PID/VID from EcuDataPv as hex string (e.g., "0x02")
     */
    private String formatPid(EcuDataPv pv) {
        if (pv == null) {
            return null;
        }

        Object pidObj = pv.get(EcuDataPv.FID_PID);
        if (pidObj instanceof Number) {
            int pidValue = ((Number) pidObj).intValue();
            return String.format(Locale.US, "0x%02X", pidValue);
        }
        return null;
    }

    private String extractMnemonic(String description) {
        if (description == null || description.isEmpty()) {
            return null;
        }

        // Log for debugging
        DebugLog.d(TAG, "Extracting mnemonic from description: " + description);

        // Map human-readable labels to mnemonics

        // VIN and related fields
        if (description.toLowerCase().contains("vehicle identification number") ||
            (description.contains("VIN") && !description.contains("Message") && !description.contains("count") && !description.contains("items"))) {
            return "vehicle_identification_number";
        }
        if (description.contains("VIN Item Count") || description.contains("Number of VIN items")) {
            return "vin_numitems";
        }
        if (description.contains("VIN Message Count") || description.contains("Message count VIN")) {
            return "counts_vin_length";
        }

        // Calibration ID fields
        if (description.contains("Calibration ID 2") || description.contains("Calibration identifier 2")) {
            return "calibration_identifier2";
        }
        if ((description.contains("Calibration ID") || description.contains("Calibration identifier") ||
             description.contains("CAL-ID")) && !description.contains("Message") && !description.contains("count") && !description.contains("items")) {
            return "calibration_identifier";
        }
        if (description.contains("CAL-ID Message Count") || description.contains("Message count CAL-ID")) {
            return "counts_calibration_identifier_length";
        }
        if (description.contains("CAL-ID Item Count") || description.contains("Number of CAL-ID items")) {
            return "calid_numitems";
        }

        // CVN fields
        if (description.contains("CVN (Calibration Verification)") ||
            (description.contains("Calibration verification") && !description.contains("Message") && !description.contains("count"))) {
            return "calibration_verification";
        }
        if (description.contains("CVN Message Count") || description.contains("Message count CVN")) {
            return "counts_calibration_verification";
        }

        // ECU Name fields
        if (description.contains("ECU name") || description.contains("ECU Name")) {
            if (description.contains("length") || description.contains("Length")) {
                return "counts_ecu_name_length";
            }
            return "ecu_name";
        }

        // IPT Message count
        if (description.contains("IPT Message Count") || description.contains("Message count IPT")) {
            return "counts_ipt";
        }

        // Monitor counters
        if (description.contains("IGNCNTR") || description.contains("Ignition Counter") ||
            description.contains("Ignition Cycles")) {
            return "IGNCNTR";
        }
        if (description.contains("OBDCOND") || description.contains("OBD Monitoring Conditions")) {
            return "OBDCOND";
        }
        // Catalyst monitors
        if (description.contains("CATCOMP1") || description.contains("Catalyst Monitor Completion") && description.contains("Bank 1")) {
            return "CATCOMP1";
        }
        if (description.contains("CATCOND1") || description.contains("Catalyst Monitor Conditions") && description.contains("Bank 1")) {
            return "CATCOND1";
        }
        if (description.contains("CATCOMP2") || description.contains("Catalyst Monitor Completion") && description.contains("Bank 2")) {
            return "CATCOMP2";
        }
        if (description.contains("CATCOND2") || description.contains("Catalyst Monitor Conditions") && description.contains("Bank 2")) {
            return "CATCOND2";
        }

        // O2 sensor monitors
        if (description.contains("O2SCOMP1") || (description.contains("O2 Sensor Monitor Completion") && description.contains("Bank 1"))) {
            return "O2SCOMP1";
        }
        if (description.contains("O2SCOND1") || (description.contains("O2 Sensor Monitor Conditions") && description.contains("Bank 1"))) {
            return "O2SCOND1";
        }
        if (description.contains("O2SCOMP2") || (description.contains("O2 Sensor Monitor Completion") && description.contains("Bank 2"))) {
            return "O2SCOMP2";
        }
        if (description.contains("O2SCOND2") || (description.contains("O2 Sensor Monitor Conditions") && description.contains("Bank 2"))) {
            return "O2SCOND2";
        }

        // Secondary O2 monitors
        if (description.contains("SO2SCOMP1") || (description.contains("Secondary O2") && description.contains("Completion") && description.contains("Bank 1"))) {
            return "SO2SCOMP1";
        }
        if (description.contains("SO2SCOND1") || (description.contains("Secondary O2") && description.contains("Conditions") && description.contains("Bank 1"))) {
            return "SO2SCOND1";
        }
        if (description.contains("SO2SCOMP2") || (description.contains("Secondary O2") && description.contains("Completion") && description.contains("Bank 2"))) {
            return "SO2SCOMP2";
        }
        if (description.contains("SO2SCOND2") || (description.contains("Secondary O2") && description.contains("Conditions") && description.contains("Bank 2"))) {
            return "SO2SCOND2";
        }

        // AIR monitor
        if (description.contains("AIRCOMP") || (description.contains("AIR Monitor") && description.contains("Completion"))) {
            return "AIRCOMP";
        }
        if (description.contains("AIRCOND") || (description.contains("AIR Monitor") && description.contains("Conditions"))) {
            return "AIRCOND";
        }

        // EVAP monitor
        if (description.contains("EVAPCOMP") || (description.contains("EVAP Monitor") && description.contains("Completion"))) {
            return "EVAPCOMP";
        }
        if (description.contains("EVAPCOND") || (description.contains("EVAP Monitor") && description.contains("Conditions"))) {
            return "EVAPCOND";
        }

        // EGR monitor
        if (description.contains("EGRCOMP") || (description.contains("EGR") && description.contains("Monitor") && description.contains("Completion"))) {
            return "EGRCOMP";
        }
        if (description.contains("EGRCOND") || (description.contains("EGR") && description.contains("Monitor") && description.contains("Conditions"))) {
            return "EGRCOND";
        }
        if (description.contains("HCCATCOMP")) return "HCCATCOMP";
        if (description.contains("HCCATCOND")) return "HCCATCOND";
        if (description.contains("NCATCOMP")) return "NCATCOMP";
        if (description.contains("NCATCOND")) return "NCATCOND";
        if (description.contains("NADSCOMP")) return "NADSCOMP";
        if (description.contains("NADSCOND")) return "NADSCOND";
        if (description.contains("PMCOMP")) return "PMCOMP";
        if (description.contains("PMCOND")) return "PMCOND";
        if (description.contains("EGSCOMP")) return "EGSCOMP";
        if (description.contains("EGSCOND")) return "EGSCOND";
        if (description.contains("BPCOMP")) return "BPCOMP";
        if (description.contains("BPCOND")) return "BPCOND";
        if (description.contains("FUELCOMP")) return "FUELCOMP";
        if (description.contains("FUELCOND")) return "FUELCOND";

        // If no specific mnemonic found, return null
        return null;
    }

    private String formatValue(String description, Object dataValue) {
        String displayValue = dataValue.toString();

        // Handle empty values
        if (displayValue.isEmpty()) {
            return "(empty)";
        }

        // Format hex values
        if (displayValue.startsWith("0x")) {
            return displayValue.toUpperCase();
        }

        // Special formatting for zeros in certain fields
        if (displayValue.equals("0") || displayValue.equals("0.0")) {
            if (description.contains("Message count") ||
                description.contains("Number of") ||
                description.contains("length")) {
                return "0";  // Show zero as-is for counts
            } else if (description.contains("Monitor")) {
                return "0";  // Show zero for monitor counts
            }
        }

        // Format numeric counters with thousands separator for large numbers
        if (description.contains("Counter") || description.contains("Counts") ||
            description.contains("CNTR") || description.contains("Conditions") ||
            description.contains("Completions")) {
            try {
                double numValue = Double.parseDouble(displayValue);
                if (numValue >= 1000) {
                    return String.format("%,.0f", numValue); // Add thousands separator
                } else {
                    return String.format("%.0f", numValue); // No separator for small numbers
                }
            } catch (NumberFormatException ignored) {
                return displayValue;
            }
        }

        // Format VIN to uppercase
        if (description.toLowerCase().contains("vehicle identification") && displayValue.length() == 17) {
            return displayValue.toUpperCase();
        }

        return displayValue;
    }

    private void addEmptyStateMessage(String title, String subtitle) {
        TextView emptyTitle = new TextView(getContext());
        emptyTitle.setText(title);
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyTitle.setTextColor(Color.parseColor("#666666"));
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dpToPx(48);
        expandedContentLayout.addView(emptyTitle, titleParams);

        TextView emptySubtitle = new TextView(getContext());
        emptySubtitle.setText(subtitle);
        emptySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        emptySubtitle.setTextColor(Color.parseColor("#444444"));
        emptySubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dpToPx(8);
        subtitleParams.leftMargin = dpToPx(32);
        subtitleParams.rightMargin = dpToPx(32);
        expandedContentLayout.addView(emptySubtitle, subtitleParams);
    }

    private void addStyledSectionHeader(String title) {
        // Add some space before section
        View spacer = new View(getContext());
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(8)
        );
        expandedContentLayout.addView(spacer, spacerParams);

        // Add section with modern card style
        LinearLayout sectionCard = new LinearLayout(getContext());
        sectionCard.setOrientation(LinearLayout.VERTICAL);
        sectionCard.setBackgroundColor(Color.parseColor("#252525"));
        sectionCard.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(4));

        TextView header = new TextView(getContext());
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTextColor(Color.parseColor("#00ACC1"));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setAllCaps(true);

        sectionCard.addView(header);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dpToPx(8);
        expandedContentLayout.addView(sectionCard, cardParams);
    }

    /**
     * Add a detail row without description support
     */
    private void addStyledDetailRow(String label, String value) {
        addStyledDetailRow(label, value, null);
    }

    /**
     * Add a detail row with optional description from messages.csv
     * @param label Display label
     * @param value Display value
     * @param mnemonicKey Key to lookup description in messages.csv (null if no description)
     */
    private void addStyledDetailRow(String label, String value, String mnemonicKey) {
        addStyledDetailRow(label, value, mnemonicKey, null);
    }

    /**
     * Add a detail row with optional description and PID display
     * @param label Display label
     * @param value Display value
     * @param mnemonicKey Key to lookup description in messages.csv (null if no description)
     * @param pidDisplay PID/VID hex string (e.g., "0x02") to show in dialog
     */
    private void addStyledDetailRow(String label, String value, String mnemonicKey, String pidDisplay) {
        if (value == null || value.isEmpty() || value.equals("Not Applicable")) {
            return;
        }

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        row.setBackgroundColor(Color.parseColor("#1F1F1F"));

        // Check if there's a description available
        if (mnemonicKey != null) {
            DebugLog.d(TAG, "Looking up description for mnemonic: " + mnemonicKey);
        }
        final String description = (mnemonicKey != null) ? Messages.getDescription(mnemonicKey) : null;
        final boolean hasDescription = (description != null && !description.isEmpty());
        if (hasDescription) {
            DebugLog.d(TAG, "Found description for " + label + ": " + description);
        } else if (mnemonicKey != null) {
            DebugLog.d(TAG, "No description found for mnemonic: " + mnemonicKey);
        }

        // Only make rows clickable in OBD Data section (when mnemonicKey or pidDisplay provided)
        // Vehicle Info section rows (no mnemonic/PID) should NOT be clickable
        if (mnemonicKey != null || pidDisplay != null) {
            // Make rows clickable (for OBD Data with mnemonic/PID info)
            row.setClickable(true);
            row.setFocusable(true);

            // Add click listener to show appropriate dialog
            final String finalPid = pidDisplay;
            row.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Add subtle haptic feedback
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    if (hasDescription) {
                        showDescriptionDialog(label, value, description, finalPid);
                    } else {
                        showUnknownPidDialog(label, value, finalPid);
                    }
                }
            });

            // Add visual indicator that row is clickable (slightly lighter background)
            row.setBackgroundColor(Color.parseColor("#222222"));
        } else {
            // Non-clickable rows (Vehicle Info with no mnemonic/PID)
            // Use standard darker background
            row.setBackgroundColor(Color.parseColor("#1F1F1F"));
        }

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTextColor(Color.parseColor("#999999"));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valueView.setTextColor(Color.parseColor("#FFFFFF"));
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setGravity(Gravity.END);
        valueView.setSingleLine(false);
        valueView.setMaxLines(2);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            2.0f
        );

        row.addView(labelView, labelParams);
        row.addView(valueView, valueParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dpToPx(3);
        expandedContentLayout.addView(row, rowParams);
    }

    /**
     * Show a dialog for unknown PIDs (those not in messages.csv)
     */
    private void showUnknownPidDialog(String label, String value, String pidDisplay) {
        // Create custom layout for the dialog
        LinearLayout dialogLayout = new LinearLayout(getContext());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dpToPx(24), dpToPx(20), dpToPx(24), dpToPx(20));
        dialogLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));

        // Unknown PID message
        TextView unknownHeader = new TextView(getContext());
        unknownHeader.setText("Unknown PID");
        unknownHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        unknownHeader.setTextColor(Color.parseColor("#666666"));
        unknownHeader.setTypeface(Typeface.DEFAULT_BOLD);
        unknownHeader.setAllCaps(true);
        unknownHeader.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams unknownHeaderParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dialogLayout.addView(unknownHeader, unknownHeaderParams);

        // Unknown PID explanation
        TextView unknownText = new TextView(getContext());
        unknownText.setText("This OBD parameter is not yet in our database. If you know what this parameter represents, you can help contribute this information to improve the app.");
        unknownText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        unknownText.setTextColor(Color.parseColor("#212121"));
        unknownText.setLineSpacing(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()), 1);
        LinearLayout.LayoutParams unknownTextParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        unknownTextParams.topMargin = dpToPx(12);
        dialogLayout.addView(unknownText, unknownTextParams);

        // Add PID/VID display if available
        if (pidDisplay != null && !pidDisplay.isEmpty()) {
            TextView pidLabel = new TextView(getContext());
            pidLabel.setText("PID/VID");
            pidLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            pidLabel.setTextColor(Color.parseColor("#666666"));
            pidLabel.setTypeface(Typeface.DEFAULT_BOLD);
            pidLabel.setAllCaps(true);
            pidLabel.setLetterSpacing(0.05f);
            LinearLayout.LayoutParams pidLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            pidLabelParams.topMargin = dpToPx(16);
            dialogLayout.addView(pidLabel, pidLabelParams);

            TextView pidText = new TextView(getContext());
            pidText.setText(pidDisplay);
            pidText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            pidText.setTextColor(Color.parseColor("#00ACC1")); // Cyan accent
            pidText.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams pidTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            pidTextParams.topMargin = dpToPx(8);
            dialogLayout.addView(pidText, pidTextParams);
        }

        // Divider
        View divider = new View(getContext());
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        );
        dividerParams.topMargin = dpToPx(20);
        dividerParams.bottomMargin = dpToPx(16);
        dialogLayout.addView(divider, dividerParams);

        // Current value section
        LinearLayout valueRow = new LinearLayout(getContext());
        valueRow.setOrientation(LinearLayout.VERTICAL);

        TextView valueLabel = new TextView(getContext());
        valueLabel.setText("Current Value");
        valueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valueLabel.setTextColor(Color.parseColor("#666666"));
        valueLabel.setTypeface(Typeface.DEFAULT_BOLD);
        valueLabel.setAllCaps(true);
        valueLabel.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams valueLabelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        valueRow.addView(valueLabel, valueLabelParams);

        TextView valueText = new TextView(getContext());
        valueText.setText(value);
        valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        valueText.setTextColor(Color.parseColor("#00ACC1"));
        valueText.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams valueTextParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        valueTextParams.topMargin = dpToPx(8);
        valueRow.addView(valueText, valueTextParams);

        dialogLayout.addView(valueRow);

        // Create and show the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(label);
        builder.setView(dialogLayout);

        // Add Contribute button (placeholder for future implementation)
        builder.setNeutralButton("Contribute Info", null);

        // Add Copy button
        builder.setNegativeButton("Copy Value", null);

        // Add Close button
        builder.setPositiveButton("Close", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Style the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#00ACC1"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#00ACC1"));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#FFA726")); // Orange for contribute

        // Override Contribute button click listener (placeholder for future implementation)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Placeholder for future contribution feature
                android.widget.Toast.makeText(getContext(), "Contribution feature coming soon!",
                    android.widget.Toast.LENGTH_SHORT).show();
                // Note: Dialog is NOT dismissed
            }
        });

        // Override Copy button click listener to prevent dialog dismissal
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Copy label and value to clipboard
                String copyText = label + ": " + value;
                if (pidDisplay != null && !pidDisplay.isEmpty()) {
                    copyText += " (PID: " + pidDisplay + ")";
                }
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText(label, copyText);
                clipboard.setPrimaryClip(clip);

                // Show a toast to confirm
                android.widget.Toast.makeText(getContext(), "Copied to clipboard",
                    android.widget.Toast.LENGTH_SHORT).show();
                // Note: Dialog is NOT dismissed
            }
        });
    }

    /**
     * Show a dialog with the description of an OBD field
     */
    private void showDescriptionDialog(String label, String value, String description) {
        showDescriptionDialog(label, value, description, null);
    }

    /**
     * Show a dialog with the description and PID of an OBD field
     */
    private void showDescriptionDialog(String label, String value, String description, String pidDisplay) {
        // Create custom layout for the dialog
        LinearLayout dialogLayout = new LinearLayout(getContext());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dpToPx(24), dpToPx(20), dpToPx(24), dpToPx(20));
        dialogLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));

        // Description section header
        TextView descHeader = new TextView(getContext());
        descHeader.setText("Description");
        descHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        descHeader.setTextColor(Color.parseColor("#666666"));
        descHeader.setTypeface(Typeface.DEFAULT_BOLD);
        descHeader.setAllCaps(true);
        descHeader.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams descHeaderParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dialogLayout.addView(descHeader, descHeaderParams);

        // Description text
        TextView descText = new TextView(getContext());
        descText.setText(description);
        descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        descText.setTextColor(Color.parseColor("#212121"));
        descText.setLineSpacing(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics()), 1);
        LinearLayout.LayoutParams descTextParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descTextParams.topMargin = dpToPx(12);
        dialogLayout.addView(descText, descTextParams);

        // Add PID/VID display if available
        if (pidDisplay != null && !pidDisplay.isEmpty()) {
            TextView pidLabel = new TextView(getContext());
            pidLabel.setText("PID/VID");
            pidLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            pidLabel.setTextColor(Color.parseColor("#666666"));
            pidLabel.setTypeface(Typeface.DEFAULT_BOLD);
            pidLabel.setAllCaps(true);
            pidLabel.setLetterSpacing(0.05f);
            LinearLayout.LayoutParams pidLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            pidLabelParams.topMargin = dpToPx(16);
            dialogLayout.addView(pidLabel, pidLabelParams);

            TextView pidText = new TextView(getContext());
            pidText.setText(pidDisplay);
            pidText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            pidText.setTextColor(Color.parseColor("#00ACC1")); // Cyan accent
            pidText.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams pidTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            pidTextParams.topMargin = dpToPx(8);
            dialogLayout.addView(pidText, pidTextParams);
        }

        // Divider
        View divider = new View(getContext());
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        );
        dividerParams.topMargin = dpToPx(20);
        dividerParams.bottomMargin = dpToPx(16);
        dialogLayout.addView(divider, dividerParams);

        // Current value section
        LinearLayout valueRow = new LinearLayout(getContext());
        valueRow.setOrientation(LinearLayout.VERTICAL);

        TextView valueLabel = new TextView(getContext());
        valueLabel.setText("Current Value");
        valueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valueLabel.setTextColor(Color.parseColor("#666666"));
        valueLabel.setTypeface(Typeface.DEFAULT_BOLD);
        valueLabel.setAllCaps(true);
        valueLabel.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams valueLabelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        valueRow.addView(valueLabel, valueLabelParams);

        TextView valueText = new TextView(getContext());
        valueText.setText(value);
        valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        valueText.setTextColor(Color.parseColor("#00ACC1"));
        valueText.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams valueTextParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        valueTextParams.topMargin = dpToPx(8);
        valueRow.addView(valueText, valueTextParams);

        dialogLayout.addView(valueRow);

        // Create and show the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(label);
        builder.setView(dialogLayout);

        // Add Copy button (set listener to null in builder, we'll override after show())
        builder.setNegativeButton("Copy Value", null);

        // Add Close button (null listener = just dismiss)
        builder.setPositiveButton("Close", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Style the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#00ACC1"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#00ACC1"));

        // Override Copy button click listener to prevent dialog dismissal
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Copy label and value to clipboard
                String copyText = label + ": " + value;
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText(label, copyText);
                clipboard.setPrimaryClip(clip);

                // Show a toast to confirm
                android.widget.Toast.makeText(getContext(), "Copied to clipboard",
                    android.widget.Toast.LENGTH_SHORT).show();
                // Note: Dialog is NOT dismissed - user can copy and continue viewing
            }
        });
    }

    private String buildPlantLocation(VehicleData data) {
        StringBuilder location = new StringBuilder();
        if (data.plantCity != null && !data.plantCity.isEmpty()) {
            location.append(data.plantCity);
        }
        if (data.plantState != null && !data.plantState.isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(data.plantState);
        }
        if (data.plantCountry != null && !data.plantCountry.isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(data.plantCountry);
        }
        return location.toString();
    }

    private void addSectionHeader(String title) {
        TextView header = new TextView(getContext());
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        header.setTextColor(Color.parseColor("#00ACC1"));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(12);
        params.bottomMargin = dpToPx(4);
        expandedContentLayout.addView(header, params);
    }

    private void addDetailRow(String label, String value) {
        if (value == null || value.isEmpty() || value.equals("Not Applicable")) {
            return;
        }

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dpToPx(2);

        TextView labelView = new TextView(getContext());
        labelView.setText(label + ":");
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTextColor(Color.parseColor("#888888"));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            dpToPx(110),
            LinearLayout.LayoutParams.WRAP_CONTENT
        );

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valueView.setTextColor(Color.parseColor("#FFFFFF"));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );

        row.addView(labelView, labelParams);
        row.addView(valueView, valueParams);
        expandedContentLayout.addView(row, rowParams);
    }

    private void updateVehicleDisplay(String vin, VehicleData vehicleData)
    {
        if (vehicleData == null)
        {
            // Show VIN only if no decoded data
            vehicleInfo.setText("VIN: " + vin);
            connectionStatus.setText("Unable to decode vehicle details");
            connectionStatus.setTextColor(Color.parseColor("#888888"));
            expandIndicator.setVisibility(View.VISIBLE); // Keep visible even without decoded data
            return;
        }

        try {
            isConnected = true;
            currentVehicleData = vehicleData;
            expandIndicator.setVisibility(View.VISIBLE);

            // Try to set manufacturer logo first
            String make = vehicleData.make;
            android.graphics.drawable.Drawable logoDrawable = AutomotiveLogoLibraryAndroid.getLogoDrawable(getContext(), make);

            if (logoDrawable != null) {
                // We have a logo - show it
                manufacturerLogo.setImageDrawable(logoDrawable);
                manufacturerLogo.setVisibility(View.VISIBLE);
                manufacturerIcon.setVisibility(View.GONE);
                // Keep no background for PNG logos
                iconContainer.setBackground(null);
            } else if (make != null && !make.isEmpty()) {
                // No logo - show first letter as fallback
                manufacturerIcon.setText(make.substring(0, 1).toUpperCase());
                manufacturerIcon.setTextColor(Color.parseColor("#00ACC1")); // Cyan when connected
                manufacturerIcon.setVisibility(View.VISIBLE);
                manufacturerLogo.setVisibility(View.GONE);
                // Add background for text icon - slightly lighter than footer for contrast
                iconContainer.setBackgroundColor(Color.parseColor("#2E2E2E"));
            }

            // Build vehicle info text
            StringBuilder info = new StringBuilder();

            // Add make
            if (vehicleData.make != null && !vehicleData.make.isEmpty())
            {
                info.append(vehicleData.make);
            }

            // Add model
            if (vehicleData.model != null && !vehicleData.model.isEmpty())
            {
                if (info.length() > 0) info.append(" ");
                info.append(vehicleData.model);
            }

            // Add year first for better readability
            if (vehicleData.modelYear != null && !vehicleData.modelYear.isEmpty())
            {
                info.insert(0, vehicleData.modelYear + " ");
            }

            vehicleInfo.setText(info.toString());
            vehicleInfo.setTextColor(Color.parseColor("#FFFFFF"));

            // Update status with engine info or VIN
            String engineDesc = vehicleData.getEngineDescription();
            String vinSuffix = (vin != null && vin.length() >= 6) ? (" • VIN: " + vin.substring(vin.length() - 6)) : "";

            if (engineDesc != null && !engineDesc.isEmpty())
            {
                connectionStatus.setText(engineDesc + vinSuffix);
            }
            else
            {
                connectionStatus.setText("Connected" + vinSuffix);
            }
            connectionStatus.setTextColor(Color.parseColor("#4CAF50")); // Green when connected

            // Update status dot to green when connected
            if (statusDot != null) {
                statusDot.getBackground().setTint(Color.parseColor("#4CAF50")); // Green when connected
            }

            // Update expanded content if currently expanded
            if (isExpanded) {
                updateExpandedContent();
            }

            notifyVehicleInfoReady();

        } catch (Exception e) {
            Log.e(TAG, "Error updating vehicle display", e);
            vehicleInfo.setText("Error reading vehicle data");
            connectionStatus.setText("Please reconnect");
            connectionStatus.setTextColor(Color.parseColor("#F44336")); // Red for error
            expandIndicator.setVisibility(View.VISIBLE); // Keep visible
        }
    }

    private void updateConnectionDisplay() {
        if (isConnected) {
            // If we have VIN/Vehicle data, updateVehicleDisplay handles the display
            return;
        }

        if (isEcuConnected) {
            VehicleManager vm = VehicleManager.getInstance();

            if (vm.hasVINRetrievalFailed()) {
                // ECU connected but VIN retrieval failed (Service 0x09 not supported)
                manufacturerLogo.setVisibility(View.GONE);
                manufacturerIcon.setVisibility(View.VISIBLE);
                manufacturerIcon.setText("OBD");
                manufacturerIcon.setTextColor(Color.parseColor("#FFA726")); // Orange for basic connection
                vehicleInfo.setText("ECU Connected");
                connectionStatus.setText("Vehicle Info not available (Mode 9 not supported)");
                connectionStatus.setTextColor(Color.parseColor("#FFA726")); // Orange
                expandIndicator.setVisibility(View.VISIBLE); // Keep visible for OBD data
                if (statusDot != null) {
                    statusDot.getBackground().setTint(Color.parseColor("#FFA726")); // Orange for partial connection
                }
            } else {
                // ECU connected, waiting for VIN retrieval
                manufacturerLogo.setVisibility(View.GONE);
                manufacturerIcon.setVisibility(View.VISIBLE);
                manufacturerIcon.setText("OBD");
                manufacturerIcon.setTextColor(Color.parseColor("#00ACC1")); // Cyan for loading
                vehicleInfo.setText("ECU Connected");
                connectionStatus.setText("Retrieving vehicle information...");
                connectionStatus.setTextColor(Color.parseColor("#00ACC1")); // Cyan for loading
                expandIndicator.setVisibility(View.VISIBLE); // Keep visible
                if (statusDot != null) {
                    statusDot.getBackground().setTint(Color.parseColor("#00ACC1")); // Cyan for loading
                }
            }
        } else {
            // No ECU connection at all
            manufacturerLogo.setVisibility(View.GONE);
            manufacturerIcon.setVisibility(View.VISIBLE);
            manufacturerIcon.setText("?");
            manufacturerIcon.setTextColor(Color.parseColor("#666666"));
            vehicleInfo.setText("No Vehicle Connected");
            connectionStatus.setText("Waiting for OBD connection...");
            connectionStatus.setTextColor(Color.parseColor("#888888"));
            expandIndicator.setVisibility(View.VISIBLE); // Keep visible for demo mode
            if (statusDot != null) {
                statusDot.getBackground().setTint(Color.parseColor("#888888")); // Gray when disconnected
            }
        }
    }

    /**
     * Get a cute emoji based on the vehicle's body class
     * @param bodyClass The body class from NHTSA data
     * @return An emoji representing the vehicle type, or null if no match
     */
    private String getVehicleEmoji(String bodyClass) {
        if (bodyClass == null) return null;

        String lowerBodyClass = bodyClass.toLowerCase();

        // SUVs and Crossovers
        if (lowerBodyClass.contains("suv") || lowerBodyClass.contains("sport utility")) {
            return "🚙";
        }
        // Trucks and Pickups
        else if (lowerBodyClass.contains("truck") || lowerBodyClass.contains("pickup")) {
            return "🛻";
        }
        // Vans and Minivans
        else if (lowerBodyClass.contains("van") || lowerBodyClass.contains("minivan")) {
            return "🚐";
        }
        // Sedans
        else if (lowerBodyClass.contains("sedan")) {
            return "🚗";
        }
        // Coupes and Sports Cars
        else if (lowerBodyClass.contains("coupe") || lowerBodyClass.contains("sports car") ||
                 lowerBodyClass.contains("sport")) {
            return "🏎️";
        }
        // Wagons
        else if (lowerBodyClass.contains("wagon")) {
            return "🚙";
        }
        // Convertibles
        else if (lowerBodyClass.contains("convertible") || lowerBodyClass.contains("cabriolet")) {
            return "🚘";
        }
        // Hatchbacks
        else if (lowerBodyClass.contains("hatchback")) {
            return "🚗";
        }
        // Buses
        else if (lowerBodyClass.contains("bus")) {
            return "🚌";
        }
        // Motorcycles
        else if (lowerBodyClass.contains("motorcycle")) {
            return "🏍️";
        }

        // Default fallback
        return "🚗";
    }

    /**
     * Add a hero section with large vehicle emoji and body class
     * @param emoji The vehicle emoji
     * @param bodyClass The body class name
     */
    private void addVehicleTypeHero(String emoji, String bodyClass) {
        // Create hero container with subtle background
        LinearLayout heroContainer = new LinearLayout(getContext());
        heroContainer.setOrientation(LinearLayout.HORIZONTAL);
        heroContainer.setGravity(Gravity.CENTER_VERTICAL);
        heroContainer.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        heroContainer.setBackgroundColor(Color.parseColor("#2A2A2A")); // Slightly lighter than footer

        // Add large emoji icon
        TextView emojiView = new TextView(getContext());
        emojiView.setText(emoji);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48); // Big and cute!
        LinearLayout.LayoutParams emojiParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        emojiParams.rightMargin = dpToPx(16);
        heroContainer.addView(emojiView, emojiParams);

        // Add body class text with styling
        TextView bodyClassView = new TextView(getContext());
        bodyClassView.setText(bodyClass);
        bodyClassView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        bodyClassView.setTextColor(Color.parseColor("#FFFFFF"));
        bodyClassView.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        heroContainer.addView(bodyClassView, textParams);

        // Add to expanded content with margins
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        heroParams.topMargin = dpToPx(8);
        heroParams.bottomMargin = dpToPx(12);
        heroParams.leftMargin = dpToPx(16);
        heroParams.rightMargin = dpToPx(16);

        expandedContentLayout.addView(heroContainer, heroParams);
    }

    private int dpToPx(int dp)
    {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getResources().getDisplayMetrics()
        );
    }
}
