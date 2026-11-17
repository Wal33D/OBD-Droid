package com.obddroid.features.fueleconomy.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.services.VehicleManager;
import com.obddroid.utils.VehicleData;
import com.obddroid.utils.VehiclePreferences;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * The fuel tank size used for range and time to empty: the user's value for this VIN, else an estimate
 * from the vehicle class, else 16 gallons, cached until the vehicle changes. Asks the user once per VIN
 * to confirm the estimate.
 */
final class TankCapacity {

    private static final Logger log = Logger.getLogger(TankCapacity.class.getName());

    /** What the screen does around the prompt. */
    interface Host {
        void showSnackbar(String message, int duration);

        /** Recompute range and time to empty after the capacity changed. */
        void refresh();
    }

    private final Activity activity;
    private final VehicleManager vehicleManager;
    private final VehiclePreferences vehiclePreferences;
    private final Host host;
    private Float cachedGallons;

    TankCapacity(Activity activity, VehicleManager vehicleManager, VehiclePreferences vehiclePreferences, Host host) {
        this.activity = activity;
        this.vehicleManager = vehicleManager;
        this.vehiclePreferences = vehiclePreferences;
        this.host = host;
    }

    /** Capacity in gallons: user-set, else estimated from the vehicle, else 16. */
    float gallons() {
        if (cachedGallons != null) {
            return cachedGallons;
        }

        String vin = vehicleManager.getCurrentVIN();
        if (vin != null && vehiclePreferences != null) {
            Float userCapacity = vehiclePreferences.getTankCapacity(vin);
            if (userCapacity != null) {
                log.info("Using user-set tank capacity: " + userCapacity + " gal");
                cachedGallons = userCapacity;
                return userCapacity;
            }
        }

        VehicleData vehicleData = vehicleManager.getCurrentVehicleData();
        if (vehicleData != null) {
            float estimated = vehicleManager.estimateTankCapacity(vehicleData);
            log.info("Using estimated tank capacity: " + estimated + " gal");
            cachedGallons = estimated;
            return estimated;
        }

        log.info("Using default tank capacity: 16.0 gal");
        cachedGallons = 16.0f;
        return 16.0f;
    }

    /** Forget the cached capacity (the vehicle changed). */
    void clearCache() {
        cachedGallons = null;
        log.fine("Cleared tank capacity cache");
    }

    /** With a decoded vehicle not asked about before, asks for its tank size after half a second. */
    void promptIfNeeded() {
        String vin = vehicleManager.getCurrentVIN();
        VehicleData vehicleData = vehicleManager.getCurrentVehicleData();

        if (vin != null && vehicleData != null &&
            vehiclePreferences != null &&
            !vehiclePreferences.hasPromptedForTankSize(vin)) {

            float estimatedCapacity = vehicleManager.estimateTankCapacity(vehicleData);
            // Let the UI settle first
            new Handler(Looper.getMainLooper()).postDelayed(
                () -> showDialog(estimatedCapacity, vin, vehicleData), 500);
        }
    }

    /** Confirm or adjust the estimate (5-100 gallons), or "Later"; either way it is not asked again. */
    private void showDialog(final float estimatedGallons, final String vin, final VehicleData vehicleData) {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.1f", estimatedGallons));
        input.setSelectAllOnFocus(true);
        input.setHint("Tank capacity (gallons)");

        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        String message = String.format(Locale.US,
                "For accurate range calculations, we need your fuel tank capacity.\n\n" +
                "Vehicle: %s\n" +
                "Estimated: %.1f gallons\n\n" +
                "Is this correct? You can adjust the value below if needed.\n\n" +
                "Note: You can change this later in settings.",
                vehicleData.getDisplayName(), estimatedGallons);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Confirm Fuel Tank Size")
                .setMessage(message)
                .setView(input)
                .setPositiveButton("Confirm", (dialogInterface, which) -> save(input, vin))
                .setNegativeButton("Later", (dialogInterface, which) -> {
                    vehiclePreferences.markTankSizePrompted(vin);
                    log.info("User deferred tank capacity confirmation");
                })
                .setCancelable(false) // Force user to make a choice
                .create();

        dialog.show();

        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void save(EditText input, String vin) {
        try {
            float capacity = Float.parseFloat(input.getText().toString().trim());

            if (capacity >= 5f && capacity <= 100f) {
                if (vehiclePreferences.setTankCapacity(vin, capacity)) {
                    vehiclePreferences.markTankSizePrompted(vin);
                    cachedGallons = capacity;
                    log.info("User confirmed tank capacity: " + capacity + " gal");

                    host.showSnackbar("Tank capacity saved: " + String.format(Locale.US, "%.1f", capacity) + " gal",
                            Snackbar.LENGTH_SHORT);
                    host.refresh();
                } else {
                    log.warning("Failed to save tank capacity");
                }
            } else {
                host.showSnackbar("Please enter a value between 5 and 100 gallons", Snackbar.LENGTH_LONG);
            }
        } catch (NumberFormatException e) {
            log.warning("Invalid tank capacity input: " + input.getText());
            host.showSnackbar("Invalid number format", Snackbar.LENGTH_SHORT);
        }
    }
}
