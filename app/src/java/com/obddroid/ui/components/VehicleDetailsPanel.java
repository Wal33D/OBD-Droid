package com.obddroid.ui.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.obddroid.common.ProcessVariables.ProcessVar;
import com.obddroid.common.ProcessVariables.TypedPvList;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.utils.VehicleData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The expanded part of {@link VehicleInfoFooter}: the decoded vehicle (identification, body, engine,
 * drivetrain, weight) and the Mode 9 system counters, drawn as styled rows into one layout. Without a
 * decoded vehicle it shows a single empty-state message. The text rules live in {@link VehicleInfoFormat}.
 */
final class VehicleDetailsPanel {

    private final Context context;
    private final LinearLayout content;

    /** @param content the layout inside the footer's scroll view; its children are replaced on each render */
    VehicleDetailsPanel(LinearLayout content) {
        this.context = content.getContext();
        this.content = content;
    }

    /**
     * Redraws the panel.
     * @param vehicle the decoded vehicle, or null before decoding
     * @param mode9 the Mode 9 (vehicle information) store, or null when the data service has none
     */
    void render(VehicleData vehicle, TypedPvList<Object, ProcessVar> mode9) {
        content.removeAllViews();

        if (vehicle != null) {
            addVehicle(vehicle, mode9);
            addSystemCounters(mode9);
        } else {
            addEmptyState("Vehicle Information",
                "Vehicle information will appear here once connected and VIN is decoded");
        }
    }

    private void addVehicle(VehicleData vehicle, TypedPvList<Object, ProcessVar> mode9) {
        addSectionHeader("Vehicle Identification");

        // The NHTSA VIN, or the Mode 9 VIN when NHTSA returned none
        String vin = vehicle.vin;
        if ((vin == null || vin.isEmpty()) && mode9 != null) {
            for (Object key : mode9.keySet()) {
                Object pvObj = mode9.get(key);
                if (pvObj instanceof EcuDataPv) {
                    EcuDataPv pv = (EcuDataPv) pvObj;
                    String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                    Object dataValue = pv.get(EcuDataPv.FID_VALUE);
                    if (description != null && dataValue != null && VehicleInfoFormat.isMode9Vin(description)) {
                        vin = String.valueOf(dataValue);
                        break;
                    }
                }
            }
        }
        addRow("VIN", vin);

        // Other VIN-related Mode 9 data, without the VIN itself and its counts
        if (mode9 != null && !mode9.isEmpty()) {
            for (Object key : mode9.keySet()) {
                Object pvObj = mode9.get(key);
                if (pvObj instanceof EcuDataPv) {
                    EcuDataPv pv = (EcuDataPv) pvObj;
                    String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                    Object dataValue = pv.get(EcuDataPv.FID_VALUE);
                    if (description != null && dataValue != null && VehicleInfoFormat.isExtraVinField(description)) {
                        addRow(description, String.valueOf(dataValue));
                    }
                }
            }
        }

        addRow("Manufacturer", VehicleInfoFormat.manufacturerName(vehicle.manufacturer));
        if (vehicle.series != null && !vehicle.series.equals("Not Applicable")) {
            addRow("Series", vehicle.series);
        }
        if (vehicle.trim != null && !vehicle.trim.equals("Not Applicable")) {
            addRow("Trim", vehicle.trim);
        }

        addSectionHeader("Body & Structure");
        String bodyClass = VehicleInfoFormat.bodyClass(vehicle.bodyClass);
        String vehicleEmoji = VehicleInfoFormat.vehicleEmoji(bodyClass);
        if (vehicleEmoji != null) {
            addVehicleTypeHero(vehicleEmoji, bodyClass);
        }
        addRow("Body Class", bodyClass);
        addRow("Vehicle Type", VehicleInfoFormat.vehicleType(vehicle.vehicleType));
        addRow("Doors", vehicle.doors);
        if (notEmpty(vehicle.wheelBase)) {
            addRow("Wheelbase", vehicle.wheelBase + " inches");
        }

        addSectionHeader("Engine & Performance");
        if (notEmpty(vehicle.engineModel)) {
            addRow("Engine Model", vehicle.engineModel);
        }
        if (vehicle.displacementL != null) {
            String displacement = vehicle.displacementL + "L";
            if (vehicle.displacementCC != null) {
                displacement += " (" + vehicle.displacementCC + "cc)";
            }
            addRow("Displacement", displacement);
        }
        addRow("Cylinders", vehicle.engineCylinders);
        if (notEmpty(vehicle.engineBrakeHp)) {
            addRow("Horsepower", vehicle.engineBrakeHp + " hp");
        }
        if (notEmpty(vehicle.topSpeed)) {
            addRow("Top Speed", vehicle.topSpeed + " mph");
        }
        addRow("Fuel Type", vehicle.fuelTypePrimary);
        if (notEmpty(vehicle.fuelDeliveryType)) {
            addRow("Fuel Injection", vehicle.fuelDeliveryType);
        }
        if (notEmpty(vehicle.valveTrainDesign)) {
            addRow("Valvetrain", vehicle.valveTrainDesign);
        }
        if (notEmpty(vehicle.coolingType)) {
            addRow("Cooling", vehicle.coolingType);
        }

        addSectionHeader("Drivetrain");
        addRow("Drive Type", vehicle.driveType);
        addRow("Transmission", vehicle.transmissionStyle);
        if (notEmpty(vehicle.transmissionSpeeds)) {
            addRow("Speeds", vehicle.transmissionSpeeds);
        }
        if (notEmpty(vehicle.axles)) {
            addRow("Axles", vehicle.axles);
        }
        if (notEmpty(vehicle.steeringLocation)) {
            addRow("Steering", vehicle.steeringLocation);
        }

        if (vehicle.gvwr != null || vehicle.curbWeight != null) {
            addSectionHeader("Weight");
            if (notEmpty(vehicle.curbWeight)) {
                addRow("Curb Weight", vehicle.curbWeight + " lbs");
            }
            if (notEmpty(vehicle.gvwr)) {
                addRow("GVWR", vehicle.gvwr);
            }
        }
    }

    /** Ignition and monitoring counters from Mode 9; nothing when there are none. */
    private void addSystemCounters(TypedPvList<Object, ProcessVar> mode9) {
        if (mode9 == null || mode9.isEmpty()) {
            return;
        }

        List<String[]> counters = new ArrayList<>();
        for (Map.Entry<Object, ProcessVar> entry : new ArrayList<>(mode9.entrySetTyped())) {
            ProcessVar storeItem = entry.getValue();
            if (!(storeItem instanceof EcuDataPv)) {
                continue;
            }
            EcuDataPv pv = (EcuDataPv) storeItem;
            String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
            Object dataValue = pv.get(EcuDataPv.FID_VALUE);
            if (description != null && dataValue != null && VehicleInfoFormat.isSystemCounter(description)) {
                counters.add(new String[]{description, String.valueOf(dataValue)});
            }
        }

        if (!counters.isEmpty()) {
            addSectionHeader("System Counters");
            for (String[] counter : counters) {
                addRow(counter[0], counter[1]);
            }
        }
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    private void addEmptyState(String title, String subtitle) {
        TextView emptyTitle = new TextView(context);
        emptyTitle.setText(title);
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyTitle.setTextColor(Color.parseColor("#666666"));
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(48);
        content.addView(emptyTitle, titleParams);

        TextView emptySubtitle = new TextView(context);
        emptySubtitle.setText(subtitle);
        emptySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        emptySubtitle.setTextColor(Color.parseColor("#444444"));
        emptySubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(8);
        subtitleParams.leftMargin = dp(32);
        subtitleParams.rightMargin = dp(32);
        content.addView(emptySubtitle, subtitleParams);
    }

    private void addSectionHeader(String title) {
        View spacer = new View(context);
        content.addView(spacer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));

        LinearLayout sectionCard = new LinearLayout(context);
        sectionCard.setOrientation(LinearLayout.VERTICAL);
        sectionCard.setBackgroundColor(Color.parseColor("#252525"));
        sectionCard.setPadding(dp(12), dp(8), dp(12), dp(4));

        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTextColor(Color.parseColor(VehicleInfoFormat.CYAN));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setAllCaps(true);
        sectionCard.addView(header);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(8);
        content.addView(sectionCard, cardParams);
    }

    /** A label/value row; skipped when the value is missing or "Not Applicable". */
    private void addRow(String label, String value) {
        if (value == null || value.isEmpty() || value.equals("Not Applicable")) {
            return;
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        row.setBackgroundColor(Color.parseColor("#1F1F1F"));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTextColor(Color.parseColor("#999999"));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );

        TextView valueView = new TextView(context);
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
        rowParams.topMargin = dp(3);
        content.addView(row, rowParams);
    }

    /** A large vehicle emoji next to the body class. */
    private void addVehicleTypeHero(String emoji, String bodyClass) {
        LinearLayout heroContainer = new LinearLayout(context);
        heroContainer.setOrientation(LinearLayout.HORIZONTAL);
        heroContainer.setGravity(Gravity.CENTER_VERTICAL);
        heroContainer.setPadding(dp(16), dp(12), dp(16), dp(12));
        heroContainer.setBackgroundColor(Color.parseColor("#2A2A2A"));

        TextView emojiView = new TextView(context);
        emojiView.setText(emoji);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        LinearLayout.LayoutParams emojiParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        emojiParams.rightMargin = dp(16);
        heroContainer.addView(emojiView, emojiParams);

        TextView bodyClassView = new TextView(context);
        bodyClassView.setText(bodyClass);
        bodyClassView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        bodyClassView.setTextColor(Color.parseColor("#FFFFFF"));
        bodyClassView.setTypeface(Typeface.DEFAULT_BOLD);
        heroContainer.addView(bodyClassView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        heroParams.topMargin = dp(8);
        heroParams.bottomMargin = dp(12);
        heroParams.leftMargin = dp(16);
        heroParams.rightMargin = dp(16);
        content.addView(heroContainer, heroParams);
    }

    private int dp(int dp) {
        return VehicleInfoHeader.dpToPx(context, dp);
    }
}
