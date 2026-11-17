package com.obddroid.features.fueleconomy.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.R;
import com.obddroid.features.fueleconomy.data.FuelEconomyPreferences;
import com.obddroid.services.VehicleManager;

import java.util.logging.Logger;

/**
 * Calibrates the speed-density VE from a real fill-up: miles driven and gallons added give the actual MPG,
 * the VE is scaled by actual/app MPG ({@link FuelEconomyFormat#calibratedVE}) and saved for the VIN.
 */
final class FuelCalibrationDialog {

    private static final Logger log = Logger.getLogger(FuelCalibrationDialog.class.getName());

    /** The screen's side of the calibration. */
    interface Host {
        /** The app's MPG to compare with: the long-term average, else the average on screen, else 25. */
        float appMpg();

        void showSnackbar(String message, int duration);
    }

    private final Activity activity;
    private final FuelEconomyPreferences preferences;
    private final VehicleManager vehicleManager;
    private final Host host;

    FuelCalibrationDialog(Activity activity, FuelEconomyPreferences preferences, VehicleManager vehicleManager,
                          Host host) {
        this.activity = activity;
        this.preferences = preferences;
        this.vehicleManager = vehicleManager;
        this.host = host;
    }

    /** @param calibratedVE the VE saved for this vehicle, or null to start from {@link FuelRateModel#DEFAULT_VE} */
    void show(Float calibratedVE) {
        log.info("Showing calibration dialog");

        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_fuel_calibration, null);

        EditText inputMilesDriven = dialogView.findViewById(R.id.input_miles_driven);
        EditText inputGallonsAdded = dialogView.findViewById(R.id.input_gallons_added);
        LinearLayout calibrationResults = dialogView.findViewById(R.id.calibration_results);
        TextView actualMpgValue = dialogView.findViewById(R.id.actual_mpg_value);
        TextView appMpgValue = dialogView.findViewById(R.id.app_mpg_value);
        TextView currentVeValue = dialogView.findViewById(R.id.current_ve_value);
        TextView newVeValue = dialogView.findViewById(R.id.new_ve_value);
        Button btnCalculate = dialogView.findViewById(R.id.btn_calculate);
        Button btnApply = dialogView.findViewById(R.id.btn_apply);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        final float currentVE = (calibratedVE != null) ? calibratedVE : FuelRateModel.DEFAULT_VE;
        currentVeValue.setText(String.format("%.1f%%", currentVE));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        final float[] calculatedNewVE = {currentVE};

        btnCalculate.setOnClickListener(v -> {
            try {
                String milesStr = inputMilesDriven.getText().toString().trim();
                String gallonsStr = inputGallonsAdded.getText().toString().trim();

                if (milesStr.isEmpty() || gallonsStr.isEmpty()) {
                    Snackbar.make(dialogView, "Please enter both miles driven and gallons added",
                            Snackbar.LENGTH_SHORT).show();
                    return;
                }

                float milesDriven = Float.parseFloat(milesStr);
                float gallonsAdded = Float.parseFloat(gallonsStr);

                if (milesDriven <= 0 || milesDriven > 1000) {
                    Snackbar.make(dialogView, "Miles driven must be between 0 and 1000", Snackbar.LENGTH_SHORT).show();
                    return;
                }
                if (gallonsAdded <= 0 || gallonsAdded > 50) {
                    Snackbar.make(dialogView, "Gallons added must be between 0 and 50", Snackbar.LENGTH_SHORT).show();
                    return;
                }

                float actualMPG = milesDriven / gallonsAdded;
                float appMPG = host.appMpg();
                float newVE = FuelEconomyFormat.calibratedVE(currentVE, actualMPG, appMPG);
                calculatedNewVE[0] = newVE;

                actualMpgValue.setText(String.format("%.1f", actualMPG));
                appMpgValue.setText(String.format("%.1f", appMPG));
                newVeValue.setText(String.format("%.1f%%", newVE));

                calibrationResults.setVisibility(View.VISIBLE);
                btnApply.setVisibility(View.VISIBLE);

                log.info(String.format("Calibration calculated: Actual MPG=%.1f, App MPG=%.1f, " +
                        "Current VE=%.1f%%, New VE=%.1f%%",
                        actualMPG, appMPG, currentVE, newVE));

            } catch (NumberFormatException e) {
                Snackbar.make(dialogView, "Invalid number format", Snackbar.LENGTH_SHORT).show();
                log.warning("Invalid number in calibration: " + e.getMessage());
            }
        });

        btnApply.setOnClickListener(v -> {
            try {
                String vin = vehicleManager.getCurrentVIN();
                if (vin == null || vin.isEmpty()) {
                    Snackbar.make(dialogView, "No VIN available - cannot save calibration",
                            Snackbar.LENGTH_LONG).show();
                    return;
                }

                if (preferences.setCalibratedVE(vin, calculatedNewVE[0])) {
                    log.info("Calibration saved successfully: VE=" + calculatedNewVE[0] + "%");
                    host.showSnackbar(String.format("✅ Calibration saved! VE adjusted to %.1f%%", calculatedNewVE[0]),
                            Snackbar.LENGTH_LONG);
                    dialog.dismiss();
                } else {
                    Snackbar.make(dialogView, "Failed to save calibration", Snackbar.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                log.warning("Error saving calibration: " + e.getMessage());
                Snackbar.make(dialogView, "Error saving calibration: " + e.getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
