package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.obddroid.R;
import com.obddroid.services.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.HelpDialogUtils;
import com.obddroid.utils.ReportFileWriter;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.utils.VehicleData;

import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity for displaying comprehensive decoded VIN information.
 * Card-based design inspired by NHTSA vPIC decoder with dark theme.
 */
public class VehicleInfoActivity extends AppCompatActivity {

    private static final String TAG = "VehicleInfoActivity";

    // UI Components
    private LinearLayout contentContainer;
    private FlexboxLayout sectionsContainer;
    private View emptyState;
    private TextView vehicleTitle;
    private TextView vehicleSubtitle;
    private ImageView vehicleIllustration;
    private VehicleInfoFooter vehicleInfoFooter;
    private View footerOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_info);

        // Set navigation bar to black
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));
        }

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("VIN Decoder");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize views
        contentContainer = findViewById(R.id.vehicle_info_content);
        sectionsContainer = findViewById(R.id.sections_container);
        emptyState = findViewById(R.id.empty_state);
        // vehicleTitle = findViewById(R.id.vehicle_title);
        // vehicleSubtitle = findViewById(R.id.vehicle_subtitle);
        // vehicleIllustration = findViewById(R.id.vehicle_illustration);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.attachBackHandler(this);
        }
        footerOverlay = findViewById(R.id.footer_overlay);

        // Set up footer overlay interaction
        if (footerOverlay != null) {
            footerOverlay.setOnClickListener(v -> {
                if (vehicleInfoFooter != null) {
                    vehicleInfoFooter.collapse();
                }
            });
        }

        // Load and display vehicle data
        loadVehicleData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vehicle_info_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_refresh_vehicle_info) {
            loadVehicleData();
            showSnackbar("Vehicle data refreshed", SnackbarHelper.MessageType.SUCCESS);
            return true;
        } else if (item.getItemId() == R.id.action_save_vehicle_info) {
            showSaveReportDialog();
            return true;
        } else if (item.getItemId() == R.id.action_info) {
            showInfoDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Load vehicle data from VehicleManager and populate the UI
     */
    private void loadVehicleData() {
        try {
            VehicleManager vm = VehicleManager.getInstance();
            VehicleData vehicleData = vm.getCurrentVehicleData();
            String vin = vm.getCurrentVIN();

            if (vehicleData == null || TextUtils.isEmpty(vin)) {
                showEmptyState();
                return;
            }

            // Hide empty state and show content
            if (emptyState != null) emptyState.setVisibility(View.GONE);
            if (contentContainer != null) contentContainer.setVisibility(View.VISIBLE);

            // Clear previous cards
            sectionsContainer.removeAllViews();

            // Populate header
            populateHeader(vin, vehicleData);

            // Populate all sections
            populateVehicleIdentification(vin, vehicleData);
            populateBodyStructure(vehicleData);
            populateEngine(vehicleData);
            populateDrivetrain(vehicleData);
            populateDimensions(vehicleData);
            populateSafety(vehicleData);
            populateAdvancedFeatures(vehicleData);
            populateManufacturing(vehicleData);
            populateWeight(vehicleData);
            populatePricing(vehicleData);
            populateMetadata(vehicleData);

        } catch (Exception e) {
            Log.e(TAG, "Error loading vehicle data", e);
            showEmptyState();
        }
    }

    private void showEmptyState() {
        if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
        if (contentContainer != null) contentContainer.setVisibility(View.GONE);
    }

    private void populateHeader(String vin, VehicleData data) {
        // Build vehicle title
        StringBuilder title = new StringBuilder();
        if (!TextUtils.isEmpty(data.modelYear)) title.append(data.modelYear).append(" ");
        if (!TextUtils.isEmpty(data.manufacturer)) {
            String mfr = data.manufacturer;
            // Shorten long manufacturer names
            if (mfr.contains("OF NORTH AMERICA")) {
                mfr = mfr.replace(" OF NORTH AMERICA, INC.", "");
            }
            title.append(mfr).append(" ");
        }
        if (!TextUtils.isEmpty(data.model)) title.append(data.model);

        if (vehicleTitle != null) {
            vehicleTitle.setText(title.toString().trim().toUpperCase());
        }

        // Build subtitle
        if (vehicleSubtitle != null && !TextUtils.isEmpty(data.bodyClass)) {
            vehicleSubtitle.setText(data.bodyClass);
        }

        // Set vehicle illustration based on bodyClass
        if (vehicleIllustration != null) {
            int iconResId = getVehicleIconResource(data.bodyClass);
            vehicleIllustration.setImageResource(iconResId);
        }
    }

    /**
     * Select the appropriate vehicle icon based on body class
     */
    private int getVehicleIconResource(String bodyClass) {
        if (TextUtils.isEmpty(bodyClass)) {
            return R.drawable.ic_vehicle_default;
        }

        String bodyClassLower = bodyClass.toLowerCase();

        // SUV / Crossover
        if (bodyClassLower.contains("suv") ||
            bodyClassLower.contains("sport utility") ||
            bodyClassLower.contains("crossover")) {
            return R.drawable.ic_vehicle_suv;
        }

        // Truck / Pickup
        if (bodyClassLower.contains("truck") ||
            bodyClassLower.contains("pickup")) {
            return R.drawable.ic_vehicle_truck;
        }

        // Van / Minivan
        if (bodyClassLower.contains("van") ||
            bodyClassLower.contains("minivan")) {
            return R.drawable.ic_vehicle_van;
        }

        // Coupe / Sports Car
        if (bodyClassLower.contains("coupe") ||
            bodyClassLower.contains("sports car") ||
            bodyClassLower.contains("convertible")) {
            return R.drawable.ic_vehicle_coupe;
        }

        // Sedan / Passenger Car
        if (bodyClassLower.contains("sedan") ||
            bodyClassLower.contains("passenger") ||
            bodyClassLower.contains("hatchback") ||
            bodyClassLower.contains("wagon")) {
            return R.drawable.ic_vehicle_sedan;
        }

        // Default
        return R.drawable.ic_vehicle_default;
    }

    private void populateVehicleIdentification(String vin, VehicleData data) {
        CardView sectionCard = createSectionCard("Vehicle Identification");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        addDetailRow(content, "VIN", vin);
        if (!TextUtils.isEmpty(data.manufacturer)) addDetailRow(content, "Manufacturer", data.manufacturer);
        if (!TextUtils.isEmpty(data.model)) addDetailRow(content, "Model", data.model);
        if (!TextUtils.isEmpty(data.modelYear)) addDetailRow(content, "Year", data.modelYear);
        if (!TextUtils.isEmpty(data.series)) addDetailRow(content, "Series", data.series);
        if (!TextUtils.isEmpty(data.trim)) addDetailRow(content, "Trim", data.trim);

        addCardToLayout(sectionCard);
    }

    private void populateBodyStructure(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.bodyClass) ||
                         !TextUtils.isEmpty(data.vehicleType) ||
                         !TextUtils.isEmpty(data.doors) ||
                         !TextUtils.isEmpty(data.wheelBase);

        if (!hasData) return;

        CardView sectionCard = createSectionCard("Body & Structure");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        if (!TextUtils.isEmpty(data.bodyClass)) addDetailRow(content, "Body Class", data.bodyClass);
        if (!TextUtils.isEmpty(data.vehicleType)) addDetailRow(content, "Vehicle Type", data.vehicleType);
        if (!TextUtils.isEmpty(data.doors)) addDetailRow(content, "Doors", data.doors);
        if (!TextUtils.isEmpty(data.wheelBase)) addDetailRow(content, "Wheelbase", data.wheelBase + " inches");

        addCardToLayout(sectionCard);
    }

    private void populateEngine(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.engineModel) ||
                         !TextUtils.isEmpty(data.displacementL) ||
                         !TextUtils.isEmpty(data.engineCylinders);

        if (!hasData) return;

        CardView sectionCard = createSectionCard("Engine & Performance");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        if (!TextUtils.isEmpty(data.engineModel)) addDetailRow(content, "Engine Model", data.engineModel);
        if (!TextUtils.isEmpty(data.engineConfiguration)) addDetailRow(content, "Configuration", data.engineConfiguration);
        if (!TextUtils.isEmpty(data.displacementL)) {
            String displacement = data.displacementL;
            if (!TextUtils.isEmpty(data.displacementCC)) {
                displacement += " (" + data.displacementCC + " cc)";
            }
            addDetailRow(content, "Displacement", displacement);
        }
        if (!TextUtils.isEmpty(data.engineCylinders)) addDetailRow(content, "Cylinders", data.engineCylinders);
        if (!TextUtils.isEmpty(data.engineStrokeCycles)) addDetailRow(content, "Stroke Cycles", data.engineStrokeCycles);
        if (!TextUtils.isEmpty(data.engineBrakeHp)) addDetailRow(content, "Horsepower", data.engineBrakeHp + " hp");
        if (!TextUtils.isEmpty(data.topSpeed)) addDetailRow(content, "Top Speed", data.topSpeed + " mph");
        if (!TextUtils.isEmpty(data.turbo)) addDetailRow(content, "Turbo", data.turbo);
        if (!TextUtils.isEmpty(data.fuelTypePrimary)) addDetailRow(content, "Fuel Type (Primary)", data.fuelTypePrimary);
        if (!TextUtils.isEmpty(data.fuelType)) addDetailRow(content, "Fuel Type", data.fuelType);
        if (!TextUtils.isEmpty(data.fuelDeliveryType)) addDetailRow(content, "Fuel Injection", data.fuelDeliveryType);
        if (!TextUtils.isEmpty(data.electrificationLevel)) addDetailRow(content, "Electrification", data.electrificationLevel);
        if (!TextUtils.isEmpty(data.valveTrainDesign)) addDetailRow(content, "Valvetrain", data.valveTrainDesign);
        if (!TextUtils.isEmpty(data.coolingType)) addDetailRow(content, "Cooling", data.coolingType);
        if (!TextUtils.isEmpty(data.engineManufacturer)) addDetailRow(content, "Engine Mfr", data.engineManufacturer);

        addCardToLayout(sectionCard);
    }

    private void populateDrivetrain(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.transmissionStyle) ||
                         !TextUtils.isEmpty(data.driveType);

        if (!hasData) return;

        CardView sectionCard = createSectionCard("Drivetrain");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        if (!TextUtils.isEmpty(data.transmissionStyle)) addDetailRow(content, "Transmission", data.transmissionStyle);
        if (!TextUtils.isEmpty(data.transmissionSpeeds)) addDetailRow(content, "Speeds", data.transmissionSpeeds);
        if (!TextUtils.isEmpty(data.driveType)) addDetailRow(content, "Drive Type", data.driveType);
        if (!TextUtils.isEmpty(data.axles)) addDetailRow(content, "Axles", data.axles);
        if (!TextUtils.isEmpty(data.steeringLocation)) addDetailRow(content, "Steering", data.steeringLocation);

        addCardToLayout(sectionCard);
    }

    private void populateDimensions(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.numberOfSeats) ||
                         !TextUtils.isEmpty(data.wheelSizeFront);

        if (!hasData) return;

        CardView sectionCard = createSectionCard("Dimensions");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        if (!TextUtils.isEmpty(data.numberOfSeats)) addDetailRow(content, "Seats", data.numberOfSeats);
        if (!TextUtils.isEmpty(data.numberOfSeatRows)) addDetailRow(content, "Seat Rows", data.numberOfSeatRows);
        if (!TextUtils.isEmpty(data.numberOfWheels)) addDetailRow(content, "Number of Wheels", data.numberOfWheels);
        if (!TextUtils.isEmpty(data.wheelSizeFront)) {
            String wheelSize = data.wheelSizeFront + "\"";
            if (!TextUtils.isEmpty(data.wheelSizeRear) &&
                !data.wheelSizeRear.equals(data.wheelSizeFront)) {
                wheelSize += " / " + data.wheelSizeRear + "\"";
            }
            addDetailRow(content, "Wheel Size", wheelSize);
        }

        addCardToLayout(sectionCard);
    }

    private void populateSafety(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.abs) ||
                         !TextUtils.isEmpty(data.backupCamera);

        if (!hasData) return;

        CardView sectionCard = createSectionCard("Safety Features");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        if (!TextUtils.isEmpty(data.abs)) addDetailRow(content, "ABS", data.abs);
        if (!TextUtils.isEmpty(data.esc)) addDetailRow(content, "Stability Control", data.esc);
        if (!TextUtils.isEmpty(data.tractionControl)) addDetailRow(content, "Traction Control", data.tractionControl);
        if (!TextUtils.isEmpty(data.dynamicBrakeSupport)) addDetailRow(content, "Dynamic Brake Support", data.dynamicBrakeSupport);
        if (!TextUtils.isEmpty(data.backupCamera)) addDetailRow(content, "Backup Camera", data.backupCamera);
        if (!TextUtils.isEmpty(data.frontAirBagLocations)) addDetailRow(content, "Front Airbags", data.frontAirBagLocations);
        if (!TextUtils.isEmpty(data.sideAirBagLocations)) addDetailRow(content, "Side Airbags", data.sideAirBagLocations);
        if (!TextUtils.isEmpty(data.pretensioner)) addDetailRow(content, "Seat Belt Pretensioner", data.pretensioner);
        if (!TextUtils.isEmpty(data.seatBeltType)) addDetailRow(content, "Seat Belt Type", data.seatBeltType);
        if (!TextUtils.isEmpty(data.otherRestraintInfo)) addDetailRow(content, "Other Restraints", data.otherRestraintInfo);
        if (!TextUtils.isEmpty(data.tpmsType)) addDetailRow(content, "TPMS", data.tpmsType);
        if (!TextUtils.isEmpty(data.daytimeRunningLight)) addDetailRow(content, "Daytime Running Lights", data.daytimeRunningLight);
        if (!TextUtils.isEmpty(data.semiautomaticHeadlampBeamSwitching)) addDetailRow(content, "Auto High Beams", data.semiautomaticHeadlampBeamSwitching);
        if (!TextUtils.isEmpty(data.automaticCrashNotification)) addDetailRow(content, "Crash Notification", data.automaticCrashNotification);
        if (!TextUtils.isEmpty(data.autoReverseSystem)) addDetailRow(content, "Auto Reverse System", data.autoReverseSystem);

        addCardToLayout(sectionCard);
    }

    private void populateAdvancedFeatures(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.adaptiveCruiseControl) ||
                         !TextUtils.isEmpty(data.blindSpotWarning);

        if (!hasData) return;

        CardView sectionCard = createSectionCard("Advanced Features");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        if (!TextUtils.isEmpty(data.adaptiveCruiseControl)) addDetailRow(content, "Adaptive Cruise Control", data.adaptiveCruiseControl);
        if (!TextUtils.isEmpty(data.forwardCollisionWarning)) addDetailRow(content, "Collision Warning", data.forwardCollisionWarning);
        if (!TextUtils.isEmpty(data.crashImminentBraking)) addDetailRow(content, "Crash Imminent Braking", data.crashImminentBraking);
        if (!TextUtils.isEmpty(data.pedestrianAEB)) addDetailRow(content, "Pedestrian Auto Braking", data.pedestrianAEB);
        if (!TextUtils.isEmpty(data.blindSpotWarning)) addDetailRow(content, "Blind Spot Warning", data.blindSpotWarning);
        if (!TextUtils.isEmpty(data.laneDepartureWarning)) addDetailRow(content, "Lane Departure Warning", data.laneDepartureWarning);
        if (!TextUtils.isEmpty(data.laneKeepingAssistance)) addDetailRow(content, "Lane Keeping Assist", data.laneKeepingAssistance);
        if (!TextUtils.isEmpty(data.parkingAssist)) addDetailRow(content, "Parking Assist", data.parkingAssist);
        if (!TextUtils.isEmpty(data.adaptiveDrivingBeam)) addDetailRow(content, "Adaptive Driving Beam", data.adaptiveDrivingBeam);
        if (!TextUtils.isEmpty(data.keylessIgnition)) addDetailRow(content, "Keyless Ignition", data.keylessIgnition);
        if (!TextUtils.isEmpty(data.activeSafetyNote)) addDetailRow(content, "Safety Notes", data.activeSafetyNote);

        addCardToLayout(sectionCard);
    }

    private void populateManufacturing(VehicleData data) {
        CardView sectionCard = createSectionCard("Manufacturing");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        // Build plant location string
        StringBuilder plantLocation = new StringBuilder();
        if (!TextUtils.isEmpty(data.plantCity)) {
            plantLocation.append(data.plantCity.toUpperCase());
        }
        if (!TextUtils.isEmpty(data.plantState)) {
            if (plantLocation.length() > 0) plantLocation.append(", ");
            plantLocation.append(data.plantState.toUpperCase());
        }
        if (!TextUtils.isEmpty(data.plantCountry)) {
            if (plantLocation.length() > 0) plantLocation.append(", ");
            String country = data.plantCountry.toUpperCase();
            if (country.contains("UNITED STATES")) {
                country = "UNITED STATES (USA)";
            }
            plantLocation.append(country);
        }

        if (plantLocation.length() > 0) {
            addDetailRow(content, "Plant Location", plantLocation.toString());
        }

        addCardToLayout(sectionCard);
    }

    private void populateWeight(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.curbWeight) ||
                         !TextUtils.isEmpty(data.gvwr);

        if (!hasData) return;

        CardView sectionCard = createSectionCard("Weight");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        if (!TextUtils.isEmpty(data.curbWeight)) {
            addDetailRow(content, "Curb Weight", data.curbWeight + " lbs");
        }

        if (!TextUtils.isEmpty(data.gvwr)) {
            // Parse GVWR to extract class and weight range
            // Format: "Class 2E: 6,001 - 7,000 lb (2,722 - 3,175 kg)"
            String gvwr = data.gvwr;
            String gvwrClass = "";
            String gvwrRangeLbs = "";
            String gvwrRangeKg = "";

            // Extract class
            if (gvwr.contains(":")) {
                String[] parts = gvwr.split(":", 2);
                gvwrClass = parts[0].trim();  // "Class 2E"
                if (parts.length > 1) {
                    String rangeWithUnits = parts[1].trim();  // "6,001 - 7,000 lb (2,722 - 3,175 kg)"

                    // Split lb and kg
                    if (rangeWithUnits.contains("(") && rangeWithUnits.contains(")")) {
                        int parenStart = rangeWithUnits.indexOf("(");
                        int parenEnd = rangeWithUnits.indexOf(")");

                        gvwrRangeLbs = rangeWithUnits.substring(0, parenStart).trim();  // "6,001 - 7,000 lb"
                        gvwrRangeKg = rangeWithUnits.substring(parenStart + 1, parenEnd).trim();  // "2,722 - 3,175 kg"
                    } else {
                        gvwrRangeLbs = rangeWithUnits;
                    }
                }
            } else {
                // No class, just range
                if (gvwr.contains("(") && gvwr.contains(")")) {
                    int parenStart = gvwr.indexOf("(");
                    int parenEnd = gvwr.indexOf(")");

                    gvwrRangeLbs = gvwr.substring(0, parenStart).trim();
                    gvwrRangeKg = gvwr.substring(parenStart + 1, parenEnd).trim();
                } else {
                    gvwrRangeLbs = gvwr;
                }
            }

            // Add parsed fields
            if (!gvwrClass.isEmpty()) {
                addDetailRow(content, "GVWR Class", gvwrClass);
            }
            if (!gvwrRangeLbs.isEmpty()) {
                addDetailRow(content, "GVWR (lbs)", gvwrRangeLbs);
            }
            if (!gvwrRangeKg.isEmpty()) {
                addDetailRow(content, "GVWR (kg)", gvwrRangeKg);
            }
        }

        addCardToLayout(sectionCard);
    }

    private void populatePricing(VehicleData data) {
        if (TextUtils.isEmpty(data.basePrice)) return;

        CardView sectionCard = createSectionCard("Pricing");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        addDetailRow(content, "Base MSRP", "$" + data.basePrice);

        addCardToLayout(sectionCard);
    }

    private void populateMetadata(VehicleData data) {
        if (TextUtils.isEmpty(data.dataSource)) return;

        CardView sectionCard = createSectionCard("Data Source");
        LinearLayout content = (LinearLayout) sectionCard.getChildAt(0);

        addDetailRow(content, "Decoder", data.dataSource);

        addCardToLayout(sectionCard);
    }

    /**
     * Add a card to the layout (single column, full width)
     */
    private void addCardToLayout(CardView card) {
        FlexboxLayout.LayoutParams cardParams = new FlexboxLayout.LayoutParams(
            FlexboxLayout.LayoutParams.MATCH_PARENT,
            FlexboxLayout.LayoutParams.WRAP_CONTENT
        );

        int margin = dpToPx(8);
        cardParams.setMargins(margin, margin / 2, margin, margin / 2);
        card.setLayoutParams(cardParams);

        sectionsContainer.addView(card);
    }

    /**
     * Create a section card with header
     */
    private CardView createSectionCard(String title) {
        CardView card = new CardView(this);
        card.setCardBackgroundColor(0xFF2C2C2C);  // Dark gray
        card.setRadius(dpToPx(12));
        card.setCardElevation(dpToPx(4));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(10));  // Slightly more horizontal, less vertical

        // Add section header
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(0xFFFFFFFF);  // White
        header.setTextSize(13);  // Compact header
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setLetterSpacing(0.03f);  // Slight letter spacing for modern look
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(0, 0, 0, dpToPx(10));  // Compact spacing
        header.setLayoutParams(headerParams);
        content.addView(header);

        card.addView(content);
        return card;
    }

    /**
     * Add a detail row (label + value) to a card
     */
    private void addDetailRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dpToPx(4), 0, dpToPx(4));  // More compact vertical spacing
        row.setLayoutParams(rowParams);

        // Label
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xFFB0BEC5);  // Lighter gray for better contrast
        labelView.setTextSize(11);  // Compact size
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        labelView.setLayoutParams(labelParams);

        // Value
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(0xFFFFFFFF);  // White
        valueView.setTextSize(11);  // Match label size for consistency
        valueView.setTypeface(null, android.graphics.Typeface.NORMAL);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        valueParams.setMarginStart(dpToPx(8));  // Small gap between label and value
        valueView.setLayoutParams(valueParams);

        row.addView(labelView);
        row.addView(valueView);
        parent.addView(row);
    }

    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Show save report dialog
     */
    private void showSaveReportDialog() {
        VehicleManager vm = VehicleManager.getInstance();
        VehicleData vehicleData = vm.getCurrentVehicleData();
        String vin = vm.getCurrentVIN();

        if (vehicleData == null || TextUtils.isEmpty(vin)) {
            showSnackbar("No vehicle data available to export", SnackbarHelper.MessageType.INFO);
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_export_ecu);
        dialog.setCancelable(true);

        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        if (dialogTitle != null) dialogTitle.setText("Save Vehicle Information");

        View csvOption = dialog.findViewById(R.id.option_export_csv);
        if (csvOption != null) {
            csvOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportCsv(vin, vehicleData);
            });
        }

        View jsonOption = dialog.findViewById(R.id.option_export_json);
        if (jsonOption != null) {
            jsonOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportJson(vin, vehicleData);
            });
        }

        View cancelButton = dialog.findViewById(R.id.btn_cancel);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /**
     * Export vehicle data to CSV
     */
    private void exportCsv(String vin, VehicleData data) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String filename = "vehicle_info_" + vin + "_" + timestamp + ".csv";
            // Built in memory, then saved through MediaStore (Documents/OBD-Droid) on Android 10+.
            StringWriter writer = new StringWriter();

            // CSV Header
            writer.append("Field,Value\n");

            // Vehicle Identification
            writer.append("VIN,\"").append(escape(vin)).append("\"\n");
            if (!TextUtils.isEmpty(data.manufacturer)) writer.append("Manufacturer,\"").append(escape(data.manufacturer)).append("\"\n");
            if (!TextUtils.isEmpty(data.model)) writer.append("Model,\"").append(escape(data.model)).append("\"\n");
            if (!TextUtils.isEmpty(data.modelYear)) writer.append("Year,\"").append(escape(data.modelYear)).append("\"\n");
            if (!TextUtils.isEmpty(data.series)) writer.append("Series,\"").append(escape(data.series)).append("\"\n");
            if (!TextUtils.isEmpty(data.trim)) writer.append("Trim,\"").append(escape(data.trim)).append("\"\n");

            // Body & Structure
            if (!TextUtils.isEmpty(data.bodyClass)) writer.append("Body Class,\"").append(escape(data.bodyClass)).append("\"\n");
            if (!TextUtils.isEmpty(data.vehicleType)) writer.append("Vehicle Type,\"").append(escape(data.vehicleType)).append("\"\n");
            if (!TextUtils.isEmpty(data.doors)) writer.append("Doors,\"").append(escape(data.doors)).append("\"\n");
            if (!TextUtils.isEmpty(data.wheelBase)) writer.append("Wheelbase,\"").append(escape(data.wheelBase)).append(" inches\"\n");

            // Engine
            if (!TextUtils.isEmpty(data.engineModel)) writer.append("Engine Model,\"").append(escape(data.engineModel)).append("\"\n");
            if (!TextUtils.isEmpty(data.engineConfiguration)) writer.append("Configuration,\"").append(escape(data.engineConfiguration)).append("\"\n");
            if (!TextUtils.isEmpty(data.displacementL)) writer.append("Displacement,\"").append(escape(data.displacementL)).append("L\"\n");
            if (!TextUtils.isEmpty(data.engineCylinders)) writer.append("Cylinders,\"").append(escape(data.engineCylinders)).append("\"\n");
            if (!TextUtils.isEmpty(data.engineBrakeHp)) writer.append("Horsepower,\"").append(escape(data.engineBrakeHp)).append(" hp\"\n");
            if (!TextUtils.isEmpty(data.fuelTypePrimary)) writer.append("Fuel Type,\"").append(escape(data.fuelTypePrimary)).append("\"\n");

            // Drivetrain
            if (!TextUtils.isEmpty(data.transmissionStyle)) writer.append("Transmission,\"").append(escape(data.transmissionStyle)).append("\"\n");
            if (!TextUtils.isEmpty(data.driveType)) writer.append("Drive Type,\"").append(escape(data.driveType)).append("\"\n");

            // Manufacturing
            if (!TextUtils.isEmpty(data.plantCountry)) writer.append("Plant Country,\"").append(escape(data.plantCountry)).append("\"\n");
            if (!TextUtils.isEmpty(data.plantCity)) writer.append("Plant City,\"").append(escape(data.plantCity)).append("\"\n");
            if (!TextUtils.isEmpty(data.plantState)) writer.append("Plant State,\"").append(escape(data.plantState)).append("\"\n");

            // Metadata
            if (!TextUtils.isEmpty(data.dataSource)) writer.append("Data Source,\"").append(escape(data.dataSource)).append("\"\n");

            writer.flush();
            writer.close();
            ReportFileWriter.writeText(this, filename, "text/csv", writer.toString());

            showSnackbar("Saved to Documents/OBD-Droid/" + filename, SnackbarHelper.MessageType.SUCCESS);
        } catch (IOException e) {
            Log.e(TAG, "Error exporting CSV", e);
            showSnackbar("Failed to export CSV: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    /**
     * Export vehicle data to JSON
     */
    private void exportJson(String vin, VehicleData data) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String filename = "vehicle_info_" + vin + "_" + timestamp + ".json";

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(data);

            ReportFileWriter.writeText(this, filename, "application/json", json);

            showSnackbar("Saved to Documents/OBD-Droid/" + filename, SnackbarHelper.MessageType.SUCCESS);
        } catch (IOException e) {
            Log.e(TAG, "Error exporting JSON", e);
            showSnackbar("Failed to export JSON: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    /**
     * Escape CSV special characters
     */
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    /**
     * Show snackbar message
     */
    private void showSnackbar(String message, SnackbarHelper.MessageType type) {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            SnackbarHelper.showSnackbar(this, message, type);
        }
    }

    private void showInfoDialog() {
        HelpDialogUtils.showHelpDialog(
            this,
            R.string.vehicle_info_info_title,
            R.string.vehicle_info_info_message,
            R.string.vehicle_info_info_ack,
            android.R.drawable.ic_menu_info_details
        );
    }
}
