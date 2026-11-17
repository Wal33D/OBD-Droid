package com.obddroid.features.fueleconomy.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.obddroid.R;
import com.obddroid.features.fueleconomy.data.FuelEconomyDataManager.HistoricalData;
import com.obddroid.features.fueleconomy.data.FuelEconomyReportExporter;

import java.util.Locale;

/**
 * The value views of activity_fuel_economy: what each metric shows, and the same values read back for
 * the saved report. The numbers on screen are the source for range, time to empty and the report, as
 * before the split.
 */
final class FuelEconomyViews {

    private final Activity activity;
    private final TextView instantMpgValue;
    private final TextView averageMpgValue;
    private final TextView fuelLevelValue;
    private final TextView rangeValue;
    private final TextView fuelFlowValue;
    private final TextView throttlePositionValue;
    private final TextView timeToEmptyValue;
    private final TextView vehicleStatusText;
    private final TextView vehicleSpeedValue;
    private final View vehicleStatusIndicator;
    private final FuelEconomyChart fuelEconomyChart;
    private final FuelFlowGauge fuelFlowGauge;

    FuelEconomyViews(Activity activity) {
        this.activity = activity;
        instantMpgValue = activity.findViewById(R.id.instant_mpg_value);
        averageMpgValue = activity.findViewById(R.id.average_mpg_value);
        fuelLevelValue = activity.findViewById(R.id.fuel_level_value);
        rangeValue = activity.findViewById(R.id.range_value);
        fuelFlowValue = activity.findViewById(R.id.fuel_flow_value);
        throttlePositionValue = activity.findViewById(R.id.throttle_position_value);
        timeToEmptyValue = activity.findViewById(R.id.time_to_empty_value);
        vehicleStatusText = activity.findViewById(R.id.vehicle_status_text);
        vehicleSpeedValue = activity.findViewById(R.id.vehicle_speed_value);
        vehicleStatusIndicator = activity.findViewById(R.id.vehicle_status_indicator);
        fuelEconomyChart = activity.findViewById(R.id.fuel_economy_chart);
        fuelFlowGauge = activity.findViewById(R.id.fuel_flow_gauge);
    }

    void showFuelLevel(float percent) {
        fuelLevelValue.setText(String.format("%.1f", percent));
    }

    /** The fuel level on screen; throws NumberFormatException before the first reading. */
    float fuelLevel() {
        return Float.parseFloat(fuelLevelValue.getText().toString());
    }

    void showFuelFlow(float galH) {
        fuelFlowValue.setText(String.format("%.1f", galH));
        fuelFlowGauge.setValue(galH);
    }

    /** The fuel flow on screen; throws NumberFormatException before the first reading. */
    float fuelFlow() {
        return Float.parseFloat(fuelFlowValue.getText().toString());
    }

    void showInstantMpg(float mpg) {
        instantMpgValue.setText(String.format("%.1f", mpg));
    }

    void showAverageMpg(float mpg) {
        averageMpgValue.setText(String.format("%.1f", mpg));
    }

    /** The average MPG on screen; throws NumberFormatException when it is not a number. */
    float averageMpg() {
        return Float.parseFloat(averageMpgValue.getText().toString());
    }

    void showRange(float miles) {
        rangeValue.setText(String.format("%.0f", miles));
    }

    void showThrottle(float percent) {
        throttlePositionValue.setText(String.format("%.0f", percent));
    }

    void showTimeToEmpty(String text) {
        timeToEmptyValue.setText(text);
    }

    /** Stopped (below 1 mph, MPG paused) or moving, with the speed and a coloured dot. */
    void showVehicleStatus(float speedMph) {
        if (speedMph < 1.0f) {
            vehicleStatusText.setText("Vehicle Stopped - MPG calculation paused");
            vehicleSpeedValue.setText("0 mph");
            vehicleStatusIndicator.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.fault_warning)));
        } else {
            vehicleStatusText.setText("Vehicle Moving - Calculating MPG");
            vehicleSpeedValue.setText(String.format(Locale.US, "%.0f mph", speedMph));
            vehicleStatusIndicator.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.fault_success)));
        }
    }

    /** The three bars: 0-5 min, 0-30 min and 0-3 h averages. */
    void showHistory(HistoricalData historical) {
        fuelEconomyChart.setData(
            new float[]{historical.recentAverage},
            new float[]{historical.mediumAverage},
            new float[]{historical.longTermAverage});
    }

    /** Some MPG is on screen (instant or average). */
    boolean showsMpg() {
        return !Float.isNaN(FuelEconomyFormat.parseNumber(instantMpgValue.getText()))
            || !Float.isNaN(FuelEconomyFormat.parseNumber(averageMpgValue.getText()));
    }

    /** Copies the values on screen into the report snapshot (NaN or null where nothing is shown). */
    void fill(FuelEconomyReportExporter.Snapshot snapshot) {
        snapshot.instantMpg = FuelEconomyFormat.parseNumber(instantMpgValue.getText());
        snapshot.averageMpg = FuelEconomyFormat.parseNumber(averageMpgValue.getText());
        snapshot.fuelLevelPercent = FuelEconomyFormat.parseNumber(fuelLevelValue.getText());
        snapshot.estimatedRangeMiles = FuelEconomyFormat.parseNumber(rangeValue.getText());
        snapshot.fuelFlowGalPerHour = FuelEconomyFormat.parseNumber(fuelFlowValue.getText());
        snapshot.throttlePercent = FuelEconomyFormat.parseNumber(throttlePositionValue.getText());
        snapshot.vehicleSpeedMph = FuelEconomyFormat.parseNumber(vehicleSpeedValue.getText());
        snapshot.timeToEmpty = FuelEconomyFormat.cleanText(timeToEmptyValue.getText());
        snapshot.statusText = FuelEconomyFormat.cleanText(vehicleStatusText.getText());
    }
}
