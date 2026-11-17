package com.obddroid.features.recalls.ui;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.R;
import com.obddroid.features.recalls.data.RecallDataManager;
import com.obddroid.features.recalls.data.RecallExporter;
import com.obddroid.features.recalls.data.RecallService;
import com.obddroid.features.recalls.model.RecallSearchResult;
import com.obddroid.services.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;

import org.json.JSONException;

import com.obddroid.features.recalls.model.AutoCheckRecallResult;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.recalllookup.core.RecallRecord;
import com.obddroid.utils.VehicleData;
import com.obddroid.utils.DebugLog;

/**
 * Screen for searching and displaying NHTSA safety recalls.
 *
 * Architecture:
 * - RecallService: Handles API calls (VIN decode + recall lookup)
 * - RecallDataManager: Handles caching and state
 * - RecallExporter: Handles CSV/JSON export
 * - RecallActivity: UI-only logic
 *
 */
public class RecallActivity extends AppCompatActivity {

    private static final String TAG = "RecallActivity";

    // Services
    private RecallService recallService;
    private RecallDataManager dataManager;
    private RecallExporter exporter;

    // UI Components
    private View loadingCard;
    private TextView statusText;
    private ProgressBar loadingIndicator;
    private LinearLayout resultsContainer;
    private View emptyState;
    private View errorCard;
    private TextView errorMessage;
    private VehicleInfoFooter vehicleInfoFooter;
    private View footerOverlay;
    private androidx.coordinatorlayout.widget.CoordinatorLayout coordinatorLayout;

    // Toggle and open recall UI
    private MaterialButtonToggleGroup recallToggleGroup;
    private MaterialButton tabAllRecallsButton;
    private MaterialButton tabOpenRecallsButton;
    private LinearLayout openRecallsContainer;
    private TextView openRecallsProviderBadge;
    private View openRecallsLoadingCard;
    private TextView openRecallsStatusText;
    private LinearLayout openRecallsResultsContainer;
    private View openRecallsEmptyState;
    private TextView openRecallsEmptyMessage;
    private View openRecallsErrorCard;
    private TextView openRecallsErrorMessageView;
    private Button openRecallsRetryButton;

    // Hero card
    private androidx.cardview.widget.CardView heroCard;
    private View heroInfoState;
    private View heroResultsState;
    private TextView heroRecallCount;
    private TextView heroVehicleText;

    // State
    private RecallSearchResult currentResult;
    private AutoCheckRecallResult openRecallsResult;
    private String currentVin;
    private boolean openRecallsLoading;
    private String openRecallsErrorMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recalls);

        // Set navigation bar to black
        if (getWindow() != null) {
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.safety_recalls);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize services
        recallService = new RecallService(this);
        dataManager = new RecallDataManager(this);
        exporter = new RecallExporter(this);

        bindViews();
        setupToggleGroup();
        setupFooterOverlay();

        // Try to load cached data
        RecallSearchResult cachedResult = loadCachedDataIfAvailable();
        if (cachedResult != null) {
            currentResult = cachedResult;
            currentVin = cachedResult.getVin();
            openRecallsResult = cachedResult.getAutoCheckRecalls();
            setOpenTabEnabled(!TextUtils.isEmpty(currentVin));
        } else {
            setOpenTabEnabled(false);
        }

        // Hide loading and error states initially
        if (loadingCard != null) loadingCard.setVisibility(View.GONE);
        if (errorCard != null) errorCard.setVisibility(View.GONE);

        // Auto-search if no cached data or cache is stale
        if (cachedResult == null || !cachedResult.isFresh()) {
            autoSearchRecalls();
        } else {
            displayResult(cachedResult);
            showSnackbar("Showing cached results (tap Refresh for latest)", SnackbarHelper.MessageType.INFO);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.recall_menu, menu);
        return true;
    }

    private void bindViews() {
        coordinatorLayout = findViewById(R.id.coordinator_layout);
        loadingCard = findViewById(R.id.recalls_loading_card);
        statusText = findViewById(R.id.recalls_status_text);
        loadingIndicator = findViewById(R.id.recalls_loading_indicator);
        resultsContainer = findViewById(R.id.recalls_results_container);
        emptyState = findViewById(R.id.recalls_empty_state);
        errorCard = findViewById(R.id.recalls_error_card);
        errorMessage = findViewById(R.id.recalls_error_message);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        footerOverlay = findViewById(R.id.footer_overlay);
        recallToggleGroup = findViewById(R.id.recalls_toggle_group);
        tabAllRecallsButton = findViewById(R.id.recalls_tab_all);
        tabOpenRecallsButton = findViewById(R.id.recalls_tab_open);
        openRecallsContainer = findViewById(R.id.open_recalls_container);
        openRecallsProviderBadge = findViewById(R.id.open_recalls_provider_badge);
        openRecallsLoadingCard = findViewById(R.id.open_recalls_loading_card);
        openRecallsStatusText = findViewById(R.id.open_recalls_status_text);
        openRecallsResultsContainer = findViewById(R.id.open_recalls_results_container);
        openRecallsEmptyState = findViewById(R.id.open_recalls_empty_state);
        openRecallsEmptyMessage = findViewById(R.id.open_recalls_empty_message);
        openRecallsErrorCard = findViewById(R.id.open_recalls_error_card);
        openRecallsErrorMessageView = findViewById(R.id.open_recalls_error_message);
        openRecallsRetryButton = findViewById(R.id.open_recalls_retry_button);
        if (openRecallsRetryButton != null) {
            openRecallsRetryButton.setOnClickListener(v -> fetchOpenRecalls());
        }

        // Hero card removed - no longer needed
        // heroCard = findViewById(R.id.recalls_hero_card);
        // heroInfoState = findViewById(R.id.hero_info_state);
        // heroResultsState = findViewById(R.id.hero_results_state);
        // heroRecallCount = findViewById(R.id.hero_recall_count);
        // heroVehicleText = findViewById(R.id.hero_vehicle_text);
    }

    private void setupToggleGroup() {
        if (recallToggleGroup != null) {
            recallToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                if (checkedId == R.id.recalls_tab_all) {
                    showAllRecallsTab();
                } else if (checkedId == R.id.recalls_tab_open) {
                    showOpenRecallsTab();
                }
            });
            recallToggleGroup.check(R.id.recalls_tab_all);
        }
    }

    private void setOpenTabEnabled(boolean enabled) {
        if (tabOpenRecallsButton != null) {
            tabOpenRecallsButton.setEnabled(enabled);
            tabOpenRecallsButton.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private boolean isAllTabSelected() {
        return recallToggleGroup == null || recallToggleGroup.getCheckedButtonId() == R.id.recalls_tab_all;
    }

    private boolean isOpenTabSelected() {
        return recallToggleGroup != null && recallToggleGroup.getCheckedButtonId() == R.id.recalls_tab_open;
    }

    private void setupFooterOverlay() {
        if (vehicleInfoFooter != null && footerOverlay != null) {
            vehicleInfoFooter.setOverlayView(footerOverlay);
        }
    }

    /**
     * Load cached data for the current VIN if available.
     */
    private RecallSearchResult loadCachedDataIfAvailable() {
        try {
            VehicleManager vm = VehicleManager.getInstance();
            String vin = vm.getCurrentVIN();
            if (!TextUtils.isEmpty(vin)) {
                return dataManager.getCurrentResult();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached data", e);
        }
        return null;
    }

    /**
     * Auto-search for recalls using connected vehicle VIN.
     */
    private void autoSearchRecalls() {
        try {
            VehicleManager vm = VehicleManager.getInstance();
            String vin = vm.getCurrentVIN();
            if (!TextUtils.isEmpty(vin)) {
                DebugLog.d(TAG, "Auto-searching recalls for VIN: " + vin);
                searchRecalls(vin);
            } else {
                DebugLog.d(TAG, "No VIN available from connected vehicle");
                showNoVehicleMessage();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting VIN from VehicleManager", e);
            showNoVehicleMessage();
        }
    }

    /**
     * Show message when no vehicle is connected.
     */
    private void showNoVehicleMessage() {
        if (heroInfoState != null) {
            for (int i = 0; i < ((LinearLayout) heroInfoState).getChildCount(); i++) {
                View child = ((LinearLayout) heroInfoState).getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setText("Connect to a vehicle to check for safety recalls");
                    break;
                }
            }
        }
        resetOpenRecallsState();
        setOpenTabEnabled(false);
    }

    /**
     * Perform recall search for the given VIN.
     */
    private void searchRecalls(String vin) {
        currentVin = vin;
        currentResult = null;
        resetOpenRecallsState();
        setOpenTabEnabled(true);

        recallService.searchRecallsByVin(vin, new RecallService.RecallSearchCallback() {
            @Override
            public void onSearchStarted() {
                runOnUiThread(() -> {
                    if (loadingCard != null) loadingCard.setVisibility(View.VISIBLE);
                    if (statusText != null) statusText.setText("Searching for recalls...");
                    resultsContainer.removeAllViews();
                    resultsContainer.setVisibility(View.GONE);
                    if (emptyState != null) emptyState.setVisibility(View.GONE);
                    if (errorCard != null) errorCard.setVisibility(View.GONE);
                });
            }

            @Override
            public void onVinDecoded(VehicleData vehicleData) {
                runOnUiThread(() -> {
                    if (statusText != null) {
                        statusText.setText("Looking up recalls for " + vehicleData.getDisplayName() + "...");
                    }
                });
            }

            @Override
            public void onSearchCompleted(RecallSearchResult result) {
                runOnUiThread(() -> {
                    if (loadingCard != null) loadingCard.setVisibility(View.GONE);

                    currentResult = result;
                    openRecallsResult = null;
                    openRecallsErrorMessage = null;
                    result.setAutoCheckRecalls(null);
                    result.setAutoCheckFetchAttempted(false);

                    // Cache the result
                    dataManager.cacheResult(result);

                    // Display the result
                    displayResult(result);
                    updateTabLabels();

                    // Show appropriate message
                    if (result.hasRecalls()) {
                        showSnackbar(result.getRecallCount() + " recall" +
                            (result.getRecallCount() == 1 ? "" : "s") + " found",
                            SnackbarHelper.MessageType.WARNING);
                    } else {
                        showSnackbar("No recalls found - vehicle is safe!",
                            SnackbarHelper.MessageType.SUCCESS);
                    }
                });
            }

            @Override
            public void onSearchFailed(String error) {
                runOnUiThread(() -> {
                    if (loadingCard != null) loadingCard.setVisibility(View.GONE);
                    resetOpenRecallsState();
                    dataManager.clearCurrentResult();
                    currentResult = null;
                    openRecallsErrorMessage = null;
                    updateHeroCard(null, false);
                    if (errorCard != null) {
                        errorCard.setVisibility(View.VISIBLE);
                        if (errorMessage != null) {
                            errorMessage.setText(error);
                        }
                    }
                    resultsContainer.setVisibility(View.GONE);
                    if (emptyState != null) emptyState.setVisibility(View.GONE);
                    updateTabLabels();
                });
            }
        });
    }

    /**
     * Display recall search result in UI.
     */
    private void displayResult(RecallSearchResult result) {
        if (result == null) {
            return;
        }

        currentResult = result;

        if (loadingCard != null) loadingCard.setVisibility(View.GONE);
        if (errorCard != null) errorCard.setVisibility(View.GONE);

        final boolean showAllTab = isAllTabSelected();

        if (resultsContainer != null) {
            resultsContainer.removeAllViews();
        }

        if (result.hasRecalls()) {
            updateHeroCard(result, true);

            if (resultsContainer != null) {
                for (RecallRecord recall : result.getRecalls()) {
                    View recallView = createRecallView(recall);
                    resultsContainer.addView(recallView);
                }
            }

            if (resultsContainer != null) {
                resultsContainer.setVisibility(showAllTab ? View.VISIBLE : View.GONE);
            }
            if (emptyState != null) {
                emptyState.setVisibility(View.GONE);
            }
        } else {
            updateHeroCard(null, false);
            if (resultsContainer != null) {
                resultsContainer.setVisibility(View.GONE);
            }
            if (emptyState != null) {
                emptyState.setVisibility(showAllTab ? View.VISIBLE : View.GONE);
            }
        }

        if (!showAllTab && openRecallsContainer != null) {
            openRecallsContainer.setVisibility(View.VISIBLE);
        }

        updateTabLabels();
    }

    private void updateTabLabels() {
        if (tabAllRecallsButton != null) {
            int totalRecalls = currentResult != null ? currentResult.getRecallCount() : 0;
            if (totalRecalls > 0) {
                tabAllRecallsButton.setText(getString(R.string.recalls_tab_all_with_count, totalRecalls));
            } else {
                tabAllRecallsButton.setText(R.string.recalls_tab_all);
            }
        }

        AutoCheckRecallResult source = openRecallsResult;
        if (source == null && currentResult != null) {
            source = currentResult.getAutoCheckRecalls();
        }

        int openCount = (source != null) ? source.getOpenRecallCount() : 0;
        if (tabOpenRecallsButton != null) {
            if (openCount > 0) {
                tabOpenRecallsButton.setText(getString(R.string.recalls_tab_open_with_count, openCount));
            } else {
                tabOpenRecallsButton.setText(R.string.recalls_tab_open);
            }
        }
    }

    /**
     * Update hero card display.
     */
    private void updateHeroCard(RecallSearchResult result, boolean hasRecalls) {
        if (heroCard == null) {
            Log.e(TAG, "heroCard is null!");
            return;
        }

        if (hasRecalls && result != null) {
            // Switch to results state - orange warning
            if (heroInfoState != null) heroInfoState.setVisibility(View.GONE);
            if (heroResultsState != null) heroResultsState.setVisibility(View.VISIBLE);

            heroCard.setCardBackgroundColor(getResources().getColor(R.color.fault_warning, null));

            if (heroRecallCount != null) {
                String text = String.format(Locale.US, "%d Recall%s Detected",
                    result.getRecallCount(), result.getRecallCount() == 1 ? "" : "s");
                heroRecallCount.setText(text);
            }

            if (heroVehicleText != null) {
                heroVehicleText.setText(result.getVehicleDisplayName());
            }
        } else {
            // Switch to info state - green
            if (heroInfoState != null) heroInfoState.setVisibility(View.VISIBLE);
            if (heroResultsState != null) heroResultsState.setVisibility(View.GONE);

            heroCard.setCardBackgroundColor(getResources().getColor(R.color.fault_success, null));
        }
    }

    /**
     * Create a recall card view.
     */
    private View createRecallView(RecallRecord recall) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_recall, resultsContainer, false);

        TextView campaignTitle = view.findViewById(R.id.recall_campaign_title);
        TextView component = view.findViewById(R.id.recall_component);
        TextView makeModel = view.findViewById(R.id.recall_make_model);
        TextView reportDate = view.findViewById(R.id.recall_report_date);
        TextView summary = view.findViewById(R.id.recall_summary);
        TextView remedy = view.findViewById(R.id.recall_remedy);
        TextView openLink = view.findViewById(R.id.recall_open_link);

        // Campaign title
        String campaignNumber = recall.getNhtsaCampaignNumber();
        if (!TextUtils.isEmpty(campaignNumber)) {
            campaignTitle.setText("Campaign #" + campaignNumber);
        }

        // Component
        if (!TextUtils.isEmpty(recall.getComponent())) {
            String componentText = recall.getComponent().replace(":", " - ");
            component.setText("Component: " + componentText);
            component.setVisibility(View.VISIBLE);
        } else {
            component.setVisibility(View.GONE);
        }

        // Make/Model
        String makeModelText = String.format("%s %s %s",
            recall.getModelYear() != null ? recall.getModelYear() : "",
            recall.getMake() != null ? recall.getMake() : "",
            recall.getModel() != null ? recall.getModel() : "").trim();
        if (!TextUtils.isEmpty(makeModelText)) {
            makeModel.setText(makeModelText);
            makeModel.setVisibility(View.VISIBLE);
        } else {
            makeModel.setVisibility(View.GONE);
        }

        // Report date
        if (!TextUtils.isEmpty(recall.getReportReceivedDate())) {
            try {
                String formattedDate = formatDate(recall.getReportReceivedDate());
                reportDate.setText("Reported: " + formattedDate);
                reportDate.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                reportDate.setVisibility(View.GONE);
            }
        } else {
            reportDate.setVisibility(View.GONE);
        }

        // Summary
        if (!TextUtils.isEmpty(recall.getSummary())) {
            summary.setText(recall.getSummary());
            summary.setVisibility(View.VISIBLE);
            view.findViewById(R.id.recall_summary_heading).setVisibility(View.VISIBLE);
        } else {
            summary.setVisibility(View.GONE);
            view.findViewById(R.id.recall_summary_heading).setVisibility(View.GONE);
        }

        // Remedy
        if (!TextUtils.isEmpty(recall.getRemedy())) {
            remedy.setText(recall.getRemedy());
            remedy.setVisibility(View.VISIBLE);
            view.findViewById(R.id.recall_remedy_heading).setVisibility(View.VISIBLE);
        } else {
            remedy.setVisibility(View.GONE);
            view.findViewById(R.id.recall_remedy_heading).setVisibility(View.GONE);
        }

        // Open link
        openLink.setOnClickListener(v -> {
            String url = "https://www.nhtsa.gov/recalls?nhtsaId=" + campaignNumber;
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                showSnackbar("Unable to open recall details", SnackbarHelper.MessageType.ERROR);
            }
        });

        return view;
    }

    private View createOpenRecallView(AutoCheckReport.RecallDetail detail) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_open_recall, openRecallsResultsContainer, false);

        TextView title = view.findViewById(R.id.open_recall_title);
        TextView status = view.findViewById(R.id.open_recall_status);
        TextView date = view.findViewById(R.id.open_recall_date);
        TextView type = view.findViewById(R.id.open_recall_type);
        TextView nhtsaNumber = view.findViewById(R.id.open_recall_nhtsa_number);
        TextView oemNumber = view.findViewById(R.id.open_recall_oem_number);
        TextView description = view.findViewById(R.id.open_recall_description);
        TextView viewDetails = view.findViewById(R.id.open_recall_open_link);

        if (detail == null) {
            return view;
        }

        if (title != null) {
            String text = !TextUtils.isEmpty(detail.campaignDescription)
                ? detail.campaignDescription
                : getString(R.string.recalls_open_default_description);
            title.setText(text);
        }

        if (status != null) {
            if (!TextUtils.isEmpty(detail.status)) {
                status.setText(getString(R.string.open_recall_status_label, detail.status));
                status.setVisibility(View.VISIBLE);
            } else {
                status.setVisibility(View.GONE);
            }
        }

        if (date != null) {
            if (!TextUtils.isEmpty(detail.recallDate)) {
                date.setText(getString(R.string.open_recall_date_label, detail.recallDate));
                date.setVisibility(View.VISIBLE);
            } else {
                date.setVisibility(View.GONE);
            }
        }

        if (type != null) {
            if (!TextUtils.isEmpty(detail.recallType)) {
                type.setText(getString(R.string.open_recall_type_label, detail.recallType));
                type.setVisibility(View.VISIBLE);
            } else {
                type.setVisibility(View.GONE);
            }
        }

        final String nhtsaId = detail.nhtsaRecallNo;
        if (nhtsaNumber != null) {
            if (!TextUtils.isEmpty(nhtsaId)) {
                nhtsaNumber.setText(getString(R.string.open_recall_nhtsa_label, nhtsaId));
                nhtsaNumber.setVisibility(View.VISIBLE);
            } else {
                nhtsaNumber.setVisibility(View.GONE);
            }
        }

        if (oemNumber != null) {
            if (!TextUtils.isEmpty(detail.oemRecallNo)) {
                oemNumber.setText(getString(R.string.open_recall_oem_label, detail.oemRecallNo));
                oemNumber.setVisibility(View.VISIBLE);
            } else {
                oemNumber.setVisibility(View.GONE);
            }
        }

        if (description != null) {
            if (!TextUtils.isEmpty(detail.campaignDescription)) {
                description.setText(detail.campaignDescription);
                description.setVisibility(View.VISIBLE);
            } else {
                description.setVisibility(View.GONE);
            }
        }

        if (viewDetails != null) {
            if (!TextUtils.isEmpty(nhtsaId)) {
                viewDetails.setVisibility(View.VISIBLE);
                viewDetails.setOnClickListener(v -> {
                    String url = "https://www.nhtsa.gov/recalls?nhtsaId=" + nhtsaId;
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        showSnackbar(getString(R.string.recalls_open_link_error), SnackbarHelper.MessageType.ERROR);
                    }
                });
            } else {
                viewDetails.setVisibility(View.GONE);
            }
        }

        return view;
    }

    private void showOpenRecallsLoading() {
        openRecallsLoading = true;
        openRecallsErrorMessage = null;
        if (openRecallsProviderBadge != null) {
            openRecallsProviderBadge.setText(R.string.open_recalls_provider_badge);
            openRecallsProviderBadge.setVisibility(View.VISIBLE);
        }
        if (openRecallsLoadingCard != null) {
            openRecallsLoadingCard.setVisibility(View.VISIBLE);
        }
        if (openRecallsStatusText != null) {
            openRecallsStatusText.setText(R.string.recalls_open_loading_message);
        }
        if (openRecallsResultsContainer != null) {
            openRecallsResultsContainer.removeAllViews();
            openRecallsResultsContainer.setVisibility(View.GONE);
        }
        if (openRecallsEmptyState != null) {
            openRecallsEmptyState.setVisibility(View.GONE);
        }
        if (openRecallsErrorCard != null) {
            openRecallsErrorCard.setVisibility(View.GONE);
        }
    }

    private void fetchOpenRecalls() {
        if (TextUtils.isEmpty(currentVin)) {
            showOpenRecallsError(getString(R.string.recalls_open_error_no_vin));
            return;
        }

        openRecallsErrorMessage = null;
        showOpenRecallsLoading();

        recallService.fetchOpenRecalls(currentVin, new RecallService.OpenRecallCallback() {
            @Override
            public void onLoading() {
                runOnUiThread(RecallActivity.this::showOpenRecallsLoading);
            }

            @Override
            public void onSuccess(AutoCheckRecallResult result) {
                runOnUiThread(() -> {
                    openRecallsLoading = false;
                    openRecallsResult = result;
                    openRecallsErrorMessage = null;
                    if (currentResult != null) {
                        currentResult.setAutoCheckRecalls(result);
                        currentResult.setAutoCheckFetchAttempted(true);
                    }
                    renderOpenRecalls();
                    updateTabLabels();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    openRecallsLoading = false;
                    openRecallsResult = null;
                    openRecallsErrorMessage = error;
                    if (currentResult != null) {
                        currentResult.setAutoCheckRecalls(null);
                        currentResult.setAutoCheckFetchAttempted(true);
                    }
                    showOpenRecallsError(error);
                    updateTabLabels();
                });
            }
        });
    }

    private void renderOpenRecalls() {
        if (openRecallsLoadingCard != null) {
            openRecallsLoadingCard.setVisibility(View.GONE);
        }
        if (openRecallsErrorCard != null) {
            openRecallsErrorCard.setVisibility(View.GONE);
        }

        if (openRecallsResultsContainer != null) {
            openRecallsResultsContainer.removeAllViews();
        }

        if (openRecallsResult == null) {
            showOpenRecallsError(getString(R.string.recalls_open_error_generic));
            return;
        }

        openRecallsErrorMessage = null;

        if (openRecallsProviderBadge != null) {
            String providerText = getString(R.string.open_recalls_provider_badge);
            String status = openRecallsResult.getStatusText();
            if (!TextUtils.isEmpty(status)) {
                providerText = providerText + " • " + status;
            }
            openRecallsProviderBadge.setText(providerText);
            openRecallsProviderBadge.setVisibility(View.VISIBLE);
        }

        if (openRecallsResult.getRecallDetails() != null && !openRecallsResult.getRecallDetails().isEmpty()) {
            if (openRecallsResultsContainer != null) {
                for (AutoCheckReport.RecallDetail detail : openRecallsResult.getRecallDetails()) {
                    View recallView = createOpenRecallView(detail);
                    openRecallsResultsContainer.addView(recallView);
                }
                openRecallsResultsContainer.setVisibility(View.VISIBLE);
            }
            if (openRecallsEmptyState != null) {
                openRecallsEmptyState.setVisibility(View.GONE);
            }
        } else if (openRecallsResult.hasCountMismatch()) {
            showOpenRecallsError(getString(R.string.recalls_open_error_mismatch));
            return;
        } else {
            String status = openRecallsResult.getStatusText();
            showOpenRecallsEmpty(!TextUtils.isEmpty(status) ? status : getString(R.string.recalls_open_empty_message));
            return;
        }

        updateTabLabels();
    }

    private void showOpenRecallsEmpty(String statusText) {
        openRecallsErrorMessage = null;
        if (openRecallsLoadingCard != null) {
            openRecallsLoadingCard.setVisibility(View.GONE);
        }
        if (openRecallsResultsContainer != null) {
            openRecallsResultsContainer.removeAllViews();
            openRecallsResultsContainer.setVisibility(View.GONE);
        }
        if (openRecallsErrorCard != null) {
            openRecallsErrorCard.setVisibility(View.GONE);
        }
        if (openRecallsEmptyState != null) {
            openRecallsEmptyState.setVisibility(View.VISIBLE);
        }
        if (openRecallsEmptyMessage != null) {
            openRecallsEmptyMessage.setText(statusText);
        }
        if (openRecallsProviderBadge != null) {
            String providerText = getString(R.string.open_recalls_provider_badge);
            if (!TextUtils.isEmpty(statusText)) {
                providerText = providerText + " • " + statusText;
            }
            openRecallsProviderBadge.setText(providerText);
            openRecallsProviderBadge.setVisibility(View.VISIBLE);
        }
    }

    private void showOpenRecallsError(String message) {
        openRecallsErrorMessage = message;
        if (openRecallsLoadingCard != null) {
            openRecallsLoadingCard.setVisibility(View.GONE);
        }
        if (openRecallsResultsContainer != null) {
            openRecallsResultsContainer.removeAllViews();
            openRecallsResultsContainer.setVisibility(View.GONE);
        }
        if (openRecallsEmptyState != null) {
            openRecallsEmptyState.setVisibility(View.GONE);
        }
        if (openRecallsErrorCard != null) {
            openRecallsErrorCard.setVisibility(View.VISIBLE);
        }
        if (openRecallsErrorMessageView != null) {
            openRecallsErrorMessageView.setText(
                !TextUtils.isEmpty(message) ? message : getString(R.string.recalls_open_error_generic)
            );
        }
        if (openRecallsProviderBadge != null) {
            openRecallsProviderBadge.setText(R.string.open_recalls_provider_badge);
            openRecallsProviderBadge.setVisibility(View.VISIBLE);
        }
    }

    private void resetOpenRecallsState() {
        openRecallsResult = null;
        openRecallsLoading = false;
        openRecallsErrorMessage = null;

        if (recallToggleGroup != null && recallToggleGroup.getCheckedButtonId() != R.id.recalls_tab_all) {
            recallToggleGroup.check(R.id.recalls_tab_all);
        }

        if (openRecallsContainer != null) {
            openRecallsContainer.setVisibility(View.GONE);
        }
        if (openRecallsProviderBadge != null) {
            openRecallsProviderBadge.setVisibility(View.GONE);
        }
        if (openRecallsResultsContainer != null) {
            openRecallsResultsContainer.removeAllViews();
            openRecallsResultsContainer.setVisibility(View.GONE);
        }
        if (openRecallsLoadingCard != null) {
            openRecallsLoadingCard.setVisibility(View.GONE);
        }
        if (openRecallsEmptyState != null) {
            openRecallsEmptyState.setVisibility(View.GONE);
        }
        if (openRecallsErrorCard != null) {
            openRecallsErrorCard.setVisibility(View.GONE);
        }
        if (tabOpenRecallsButton != null) {
            tabOpenRecallsButton.setText(R.string.recalls_tab_open);
        }
    }

    private void showAllRecallsTab() {
        if (openRecallsContainer != null) {
            openRecallsContainer.setVisibility(View.GONE);
        }
        if (openRecallsProviderBadge != null) {
            openRecallsProviderBadge.setVisibility(View.GONE);
        }
        if (currentResult != null) {
            displayResult(currentResult);
        } else {
            if (resultsContainer != null) {
                resultsContainer.setVisibility(View.GONE);
            }
            if (emptyState != null) {
                emptyState.setVisibility(View.GONE);
            }
        }
    }

    private void showOpenRecallsTab() {
        if (openRecallsContainer != null) {
            openRecallsContainer.setVisibility(View.VISIBLE);
        }
        if (openRecallsProviderBadge != null) {
            openRecallsProviderBadge.setVisibility(View.VISIBLE);
        }
        if (resultsContainer != null) {
            resultsContainer.setVisibility(View.GONE);
        }
        if (emptyState != null) {
            emptyState.setVisibility(View.GONE);
        }

        AutoCheckRecallResult cached = currentResult != null ? currentResult.getAutoCheckRecalls() : null;
        if (cached != null && openRecallsResult == null) {
            openRecallsResult = cached;
        }

        if (openRecallsResult != null) {
            renderOpenRecalls();
        } else if (openRecallsLoading) {
            showOpenRecallsLoading();
        } else if (openRecallsErrorMessage != null) {
            showOpenRecallsError(openRecallsErrorMessage);
        } else if (!TextUtils.isEmpty(currentVin)) {
            fetchOpenRecalls();
        } else {
            showOpenRecallsError(getString(R.string.recalls_open_error_no_vin));
        }
    }

    /**
     * Show save report dialog.
     */
    private void showSaveReportDialog() {
        RecallSearchResult result = dataManager.getCurrentResult();
        if (result == null || !result.hasRecalls()) {
            showSnackbar("Search for recalls before saving a report", SnackbarHelper.MessageType.INFO);
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_export_ecu);
        dialog.setCancelable(true);

        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        if (dialogTitle != null) dialogTitle.setText("Save Report");

        View csvOption = dialog.findViewById(R.id.option_export_csv);
        if (csvOption != null) {
            csvOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportCsv();
            });
        }

        View jsonOption = dialog.findViewById(R.id.option_export_json);
        if (jsonOption != null) {
            jsonOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportJson();
            });
        }

        View cancelButton = dialog.findViewById(R.id.btn_cancel);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /**
     * Export recalls to CSV.
     */
    private void exportCsv() {
        RecallSearchResult result = dataManager.getCurrentResult();
        if (result == null || !result.hasRecalls()) {
            showSnackbar("No recall data available to export", SnackbarHelper.MessageType.INFO);
            return;
        }

        try {
            String filename = exporter.exportToCsv(result);
            showSnackbar("Recall report saved: " + filename, SnackbarHelper.MessageType.SUCCESS);
        } catch (IOException e) {
            showSnackbar("Failed to export CSV: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    /**
     * Export recalls to JSON.
     */
    private void exportJson() {
        RecallSearchResult result = dataManager.getCurrentResult();
        if (result == null || !result.hasRecalls()) {
            showSnackbar("No recall data available to export", SnackbarHelper.MessageType.INFO);
            return;
        }

        try {
            String filename = exporter.exportToJson(result);
            showSnackbar("Recall report saved: " + filename, SnackbarHelper.MessageType.SUCCESS);
        } catch (IOException | JSONException e) {
            showSnackbar("Failed to export JSON: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    /**
     * Format date string for display.
     */
    private String formatDate(String dateString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (ParseException e) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                Date date = inputFormat.parse(dateString);
                return outputFormat.format(date);
            } catch (ParseException e2) {
                return dateString;
            }
        }
    }

    /**
     * Show a snackbar properly positioned above footer.
     */
    private void showSnackbar(String message, SnackbarHelper.MessageType type) {
        if (coordinatorLayout == null) {
            SnackbarHelper.showSnackbar(this, message, type);
            return;
        }

        Snackbar snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(Color.parseColor("#757575"));

        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(Color.WHITE);
        }

        // Set bottom margin to appear above footer (56dp)
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params =
            (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) snackbarView.getLayoutParams();
        params.setMargins(0, 0, 0, (int) (56 * getResources().getDisplayMetrics().density));
        snackbarView.setLayoutParams(params);

        snackbar.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_refresh_recalls) {
            autoSearchRecalls();
            return true;
        } else if (id == R.id.action_save_report) {
            showSaveReportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
