package com.obddroid.ui.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.SearchManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.obddroid.R;
import com.obddroid.obd.ElmProt;
import com.obddroid.services.CommService;
import com.obddroid.services.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.utils.VehicleData;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Activity for displaying detailed information and actions for a specific fault code.
 * Provides options to view freeze frame, search web, watch videos, ask AI, and copy code.
 * Web searches are enhanced with vehicle year/make/model context for more relevant results.
 */
public class FaultCodeDetailsActivity extends AppCompatActivity {

    private static final Logger log = Logger.getLogger(FaultCodeDetailsActivity.class.getSimpleName());

    // Intent extras keys
    public static final String EXTRA_FAULT_CODE = "fault_code";
    public static final String EXTRA_FAULT_DESCRIPTION = "fault_description";
    public static final String EXTRA_IS_PENDING = "is_pending";
    public static final String EXTRA_HAS_FREEZE = "has_freeze";
    public static final String EXTRA_DTC_VALUE = "dtc_value";

    // UI components
    private ImageView statusIcon;
    private TextView codeNumber;
    private TextView codeDescription;
    private TextView codeType;
    private LinearLayout freezeFrameOption;
    private LinearLayout searchWebOption;
    private LinearLayout nondaVideoOption;
    private LinearLayout askAiOption;
    private LinearLayout copyCodeOption;
    private TextView freezeFrameStatus;
    private TextView nondaVideoStatus;
    private TextView askAiStatus;
    private VehicleInfoFooter vehicleInfoFooter;

    // Fault code data
    private String faultCode;
    private String faultDescription;
    private boolean isPending;
    private boolean hasFreeze;
    private int dtcValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fault_code_details);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.background_secondary));
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.fault_codes_details_title);
        }

        // Get fault code data from intent
        Intent intent = getIntent();
        faultCode = intent.getStringExtra(EXTRA_FAULT_CODE);
        faultDescription = intent.getStringExtra(EXTRA_FAULT_DESCRIPTION);
        isPending = intent.getBooleanExtra(EXTRA_IS_PENDING, false);
        hasFreeze = intent.getBooleanExtra(EXTRA_HAS_FREEZE, false);
        dtcValue = intent.getIntExtra(EXTRA_DTC_VALUE, 0);

        if (faultCode == null || faultDescription == null) {
            log.severe("FaultCodeDetailsActivity started without required extras");
            finish();
            return;
        }

        initializeViews();
        setupFaultCodeInfo();
        setupClickListeners();
        setupVehicleFooter();
    }

    private void initializeViews() {
        statusIcon = findViewById(R.id.fault_code_status_icon);
        codeNumber = findViewById(R.id.fault_code_number);
        codeDescription = findViewById(R.id.fault_code_description);
        codeType = findViewById(R.id.fault_code_type);
        freezeFrameOption = findViewById(R.id.option_freeze_frame);
        searchWebOption = findViewById(R.id.option_search_web);
        nondaVideoOption = findViewById(R.id.option_watch_nonda);
        askAiOption = findViewById(R.id.option_ask_ai);
        copyCodeOption = findViewById(R.id.option_copy_code);
        freezeFrameStatus = findViewById(R.id.freeze_frame_status);
        nondaVideoStatus = findViewById(R.id.nonda_video_status);
        askAiStatus = findViewById(R.id.ask_ai_status);
    }

    private void setupFaultCodeInfo() {
        codeNumber.setText(faultCode);
        codeDescription.setText(faultDescription);

        // Set code type and icon based on pending status
        if (isPending) {
            codeType.setText(R.string.fault_codes_type_pending);
            statusIcon.setImageResource(android.R.drawable.ic_menu_recent_history);
            statusIcon.setColorFilter(Color.parseColor("#FF9800"));
        } else {
            codeType.setText(R.string.fault_codes_type_confirmed);
            statusIcon.setImageResource(android.R.drawable.ic_menu_myplaces);
            statusIcon.setColorFilter(Color.parseColor("#F57C00"));
        }
    }

    private void setupClickListeners() {
        // Check connection state
        boolean isConnected = CommService.elm != null &&
                (CommService.elm.getStatus() == ElmProt.STAT.CONNECTED ||
                 CommService.elm.getStatus() == ElmProt.STAT.ECU_DETECTED);

        // Freeze Frame Option
        if (!isConnected || !hasFreeze) {
            freezeFrameStatus.setText(!isConnected ?
                    R.string.fault_codes_freeze_connect_first :
                    R.string.fault_codes_freeze_not_available);
            freezeFrameOption.setAlpha(0.5f);
            freezeFrameOption.setEnabled(false);
            freezeFrameOption.setClickable(false);
        } else {
            freezeFrameOption.setOnClickListener(v -> showFreezeFrame());
        }

        // Search Web Option
        searchWebOption.setOnClickListener(v -> searchFaultCodeOnWeb());

        // Nonda Video Option
        boolean hasDirectVideo = FaultCodeUiHelper.hasDirectNondaVideo(faultCode);
        nondaVideoStatus.setText(hasDirectVideo ?
                R.string.fault_codes_nonda_watch_guide :
                R.string.fault_codes_nonda_search_channel);
        nondaVideoOption.setOnClickListener(v -> openNondaVideo());

        // Ask CoPilot Option - launches CoPilot with fault code context
        askAiOption.setOnClickListener(v -> askCoPilotAboutCode());

        // Copy Code Option
        copyCodeOption.setOnClickListener(v -> copyCodeToClipboard());
    }

    private void setupVehicleFooter() {
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.attachBackHandler(this);
            vehicleInfoFooter.setOverlayView(findViewById(R.id.footer_overlay));
        }
    }

    private void showFreezeFrame() {
        // Use the helper method from FaultCodeUiHelper to show freeze frame
        // Create a FaultCodeInfo object to pass to the helper
        com.obddroid.services.FaultCodeService.FaultCodeInfo faultCodeInfo =
                new com.obddroid.services.FaultCodeService.FaultCodeInfo(
                        faultCode, faultDescription, isPending, hasFreeze, dtcValue);

        FaultCodeUiHelper.showFreezeFrameDialogForCode(this, faultCodeInfo);
    }

    private void searchFaultCodeOnWeb() {
        try {
            // Build intelligent search query with vehicle context
            StringBuilder query = new StringBuilder();

            // Get vehicle data from VehicleManager
            VehicleData vehicleData = VehicleManager.getInstance().getCurrentVehicleData();

            // Include vehicle make, model, year if available for more relevant results
            if (vehicleData != null) {
                if (vehicleData.modelYear != null && !vehicleData.modelYear.isEmpty()) {
                    query.append(vehicleData.modelYear).append(" ");
                }
                if (vehicleData.make != null && !vehicleData.make.isEmpty()) {
                    query.append(capitalizeWords(vehicleData.make)).append(" ");
                }
                if (vehicleData.model != null && !vehicleData.model.isEmpty()) {
                    query.append(capitalizeWords(vehicleData.model)).append(" ");
                }
            }

            // Add fault code
            query.append(faultCode);

            // Log the enhanced search query
            log.info("Searching web for: " + query.toString());

            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY, query.toString());
            startActivity(intent);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to search web for fault code", e);
            SnackbarHelper.showError(this, getString(R.string.fault_codes_web_search_failed));
        }
    }

    /**
     * Capitalizes the first letter of each word in a string and removes dashes.
     * Handles cases like:
     * - "HONDA CIVIC" -> "Honda Civic"
     * - "MERCEDES-BENZ" -> "Mercedes Benz"
     * - "mercedes-benz" -> "Mercedes Benz"
     */
    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Replace dashes with spaces for better search results
        text = text.replace('-', ' ');

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    private void openNondaVideo() {
        String videoUrl = FaultCodeUiHelper.getNondaVideoUrl(faultCode);
        if (videoUrl == null) {
            SnackbarHelper.showWarning(this, getString(R.string.fault_codes_nonda_not_found));
            return;
        }

        boolean hasDirectVideo = FaultCodeUiHelper.hasDirectNondaVideo(faultCode);
        if (!hasDirectVideo) {
            SnackbarHelper.showInfo(this,
                    getString(R.string.fault_codes_nonda_searching, faultCode),
                    SnackbarHelper.Duration.SHORT);
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl));
            startActivity(intent);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to open nonda video", e);
            SnackbarHelper.showError(this, getString(R.string.fault_codes_nonda_failed));
        }
    }

    private void askCoPilotAboutCode() {
        FaultCodeUiHelper.launchCoPilotForFaultCode(this, faultCode, faultDescription);
    }

    private void copyCodeToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            String text = faultCode + " - " + faultDescription;
            ClipData clip = ClipData.newPlainText("OBD Fault Code", text);
            clipboard.setPrimaryClip(clip);
            SnackbarHelper.showSuccess(this,
                    getString(R.string.fault_codes_copied, faultCode),
                    SnackbarHelper.Duration.SHORT);
        } else {
            SnackbarHelper.showError(this, getString(R.string.fault_codes_copy_failed));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
